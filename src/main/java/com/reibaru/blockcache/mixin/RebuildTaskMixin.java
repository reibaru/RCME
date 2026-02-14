package com.reibaru.blockcache.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.reibaru.blockcache.BlockCacheState;
import com.reibaru.blockcache.CacheManager;
import com.reibaru.blockcache.CachedMesh;
import com.reibaru.blockcache.api.VertexBufferCacheApi;
import com.reibaru.blockcache.client.gpu.tile.TileManager;
import com.reibaru.blockcache.mixin.accessor.RebuildTaskAccessor;
import com.reibaru.blockcache.mixin.accessor.RenderChunkAccessor;
import com.reibaru.blockcache.api.DirtyBlockHolder;
import com.reibaru.blockcache.render.DiffChunkCompiler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.fml.loading.FMLPaths;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class RebuildTaskMixin {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    // =====================================================
    // RenderType 統合
    // =====================================================

    @Unique
    private static RenderType blockcache$unify(RenderType type) {
        if (type == RenderType.cutout() || type == RenderType.cutoutMipped()) {
            return RenderType.cutout();
        }
        return type;
    }

    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BakedModel;getRenderTypes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;)Lnet/minecraftforge/client/ChunkRenderTypeSet;"))
    private ChunkRenderTypeSet blockcache$redirectRenderTypes(
            BakedModel model,
            BlockState state,
            RandomSource random,
            ModelData modelData) {
        List<RenderType> unified = new ArrayList<>();
        for (RenderType rt : model.getRenderTypes(state, random, modelData)) {
            unified.add(blockcache$unify(rt));
        }
        return ChunkRenderTypeSet.of(unified.toArray(RenderType[]::new));
    }

    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ChunkBufferBuilderPack;builder(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"))
    private BufferBuilder blockcache$redirectBuilder(
            ChunkBufferBuilderPack pack,
            RenderType type) {
        return pack.builder(blockcache$unify(type));
    }

    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"))
    private void blockcache$redirectRenderBatched(
            BlockRenderDispatcher dispatcher,
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter region,
            PoseStack pose,
            VertexConsumer consumer,
            boolean checkSides,
            RandomSource random,
            ModelData data,
            RenderType type) {
        dispatcher.renderBatched(
                state, pos, region, pose, consumer,
                checkSides, random, data,
                blockcache$unify(type));
    }

    // =====================================================
    //  差分コンパイル本体（compile HEAD）
    // =====================================================

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)

    private void blockcache$diffCompile(
            float camX,
            float camY,
            float camZ,
            ChunkBufferBuilderPack pack,
            CallbackInfoReturnable<Object> cir) {
        var chunk = blockcache$getOwnerRenderChunk();
        if (chunk == null)
            return;

        var dirty = ((DirtyBlockHolder) (Object) chunk).blockcache$getDirtyBlocks();
        if (dirty.isEmpty())
            return;

        // ★ TileManager に dirty block を通知
        for (int packed : dirty) {
            int localX = packed & 15;
            int localY = (packed >> 4) & 15;
            int localZ = (packed >> 8) & 15;

            BlockPos origin = chunk.getOrigin();
            int baseX = origin.getX();
            int baseY = origin.getY();
            int baseZ = origin.getZ();

            BlockPos worldPos = new BlockPos(
                    baseX + localX,
                    baseY + localY,
                    baseZ + localZ);

            TileManager.INSTANCE.markDirtyFromBlock(worldPos);
        }

        if (Minecraft.getInstance().level == null)
            return;

        RenderChunkRegion region = ((RebuildTaskAccessor) (Object) this).blockcache$getRegion();

        Object results = DiffChunkCompiler.compile(
                chunk, dirty, camX, camY, camZ, pack, region);

        cir.setReturnValue(results);
    }

    // =====================================================
    // キャッシュ利用（doTask HEAD）
    // =====================================================

    @Inject(method = "doTask", at = @At("HEAD"), cancellable = true)
    private void blockcache$useCachedMesh(
            ChunkBufferBuilderPack pack,
            CallbackInfoReturnable<CompletableFuture<?>> cir) {
        var chunk = blockcache$getOwnerRenderChunk();
        if (chunk == null)
            return;

        if (blockcache$applyCachedMesh(chunk)) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
            return;
        }

        if (blockcache$skipIfCameraStill()) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }

    @Unique
    private boolean blockcache$applyCachedMesh(ChunkRenderDispatcher.RenderChunk chunk) {
        RenderChunkAccessor acc = (RenderChunkAccessor) (Object) chunk;

        ChunkPos pos = new ChunkPos(acc.blockcache$getOrigin());
        Map<RenderType, CachedMesh> cached = CacheManager.getCachedMeshes(pos.x, pos.z);

        if (cached == null || cached.isEmpty())
            return false;

        Map<RenderType, VertexBuffer> buffers = acc.blockcache$getBuffers();

        for (var entry : cached.entrySet()) {
            RenderType layer = entry.getKey();
            CachedMesh mesh = entry.getValue();

            VertexBuffer vb = buffers.get(layer);
            if (vb == null || mesh.indexCount() == 0)
                continue;

            VertexBufferCacheApi api = (VertexBufferCacheApi) (Object) vb;
            api.blockcache$setUsingCachedMesh(true);
            api.blockcache$uploadFromCachedMesh(mesh);
            api.blockcache$setUsingCachedMesh(false);
        }

        return true;
    }

    @Unique
    private boolean blockcache$skipIfCameraStill() {
        return !BlockCacheState.positionChangedEnough()
                && !BlockCacheState.angleChangedEnough();
    }

    // =====================================================
    // バニラ compile の TAIL（メッシュ抽出 & 保存）
    // =====================================================

    @Inject(method = "compile", at = @At("TAIL"))

    private void blockcache$afterCompile(
            float camX,
            float camY,
            float camZ,
            ChunkBufferBuilderPack pack,
            CallbackInfoReturnable<Object> cir) {
        Object results = cir.getReturnValue();
        System.out.println("[BlockCache] afterCompile invoked for chunk; results="
                + (results == null ? "null" : results.getClass().getSimpleName()));
        if (results == null) {
            System.out.println("[BlockCache] afterCompile: results is null, skipping save");
            clearDirty();
            return;
        }

        try {
            var field = results.getClass().getDeclaredField("renderedLayers");
            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<RenderType, BufferBuilder.RenderedBuffer> layers = (Map<RenderType, BufferBuilder.RenderedBuffer>) field
                    .get(results);

            if (!layers.isEmpty()) {
                System.out.println("[BlockCache] afterCompile: found renderedLayers size=" + layers.size());
                for (var e : layers.entrySet()) {
                    RenderType t = e.getKey();
                    BufferBuilder.RenderedBuffer b = e.getValue();
                    int vbSize = b.vertexBuffer() == null ? 0 : b.vertexBuffer().remaining();
                    int ibSize = b.indexBuffer() == null ? 0 : b.indexBuffer().remaining();
                    System.out.println("[BlockCache] afterCompile: layer=" + safeRenderType(t) + " vb=" + vbSize
                            + " ib=" + ibSize);
                }
                var chunk = blockcache$getOwnerRenderChunk();
                if (chunk != null) {
                    RenderChunkAccessor acc = (RenderChunkAccessor) (Object) chunk;
                    ChunkPos pos = new ChunkPos(acc.blockcache$getOrigin());
                    System.out.println("[BlockCache] afterCompile: saving chunk mesh for " + pos.x + "," + pos.z);
                    saveChunkMesh(pos, layers);
                    TileManager.INSTANCE.updateAirMapFromMesh(pos, layers);
                }
            }

        } catch (Exception e) {
            LOGGER.warn("[BlockCache] Error in compile TAIL", e);
        } finally {
            clearDirty();
        }
    }

    // =====================================================
    // Unique メソッド群
    // =====================================================

    @Unique
    private ChunkRenderDispatcher.RenderChunk blockcache$getOwnerRenderChunk() {
        try {
            var field = this.getClass().getDeclaredField("this$1");
            field.setAccessible(true);
            return (ChunkRenderDispatcher.RenderChunk) field.get(this);
        } catch (Exception e) {
            LOGGER.warn("[BlockCache] Failed to resolve outer RenderChunk", e);
            return null;
        }
    }

    @Unique
    private void clearDirty() {
        var chunk = blockcache$getOwnerRenderChunk();
        if (chunk == null)
            return;
        ((DirtyBlockHolder) (Object) chunk).blockcache$getDirtyBlocks().clear();
    }

    @Unique
    private void saveChunkMesh(
            ChunkPos pos,
            Map<RenderType, BufferBuilder.RenderedBuffer> layers) throws Exception {

        Path dir = FMLPaths.GAMEDIR.get().resolve("blockcache/cache");
        Files.createDirectories(dir);

        int cx = pos.x;
        int cz = pos.z;

        for (var entry : layers.entrySet()) {
            RenderType type = entry.getKey();
            BufferBuilder.RenderedBuffer buf = entry.getValue();

            ByteBuffer vb = buf.vertexBuffer();
            ByteBuffer ib = buf.indexBuffer();
            if (ib == null || !ib.hasRemaining())
                continue;

            if (vb != null)
                vb.rewind();
            ib.rewind();

            String name = safeRenderType(type);
            Path file = dir.resolve(String.format("chunk_%d_%d_%s.bin", cx, cz, name));

            try (FileChannel ch = FileChannel.open(
                    file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer vbSize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                vbSize.putInt(vb != null ? vb.remaining() : 0).flip();
                ch.write(vbSize);
                if (vb != null)
                    ch.write(vb);

                ByteBuffer ibSize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                ibSize.putInt(ib.remaining()).flip();
                ch.write(ibSize);
                ch.write(ib);
            }
        }
    }

    @Unique
    private String safeRenderType(RenderType type) {
        String name = type.toString().toLowerCase();
        if (name.contains("solid"))
            return "solid";
        if (name.contains("cutout_mipped"))
            return "cutout_mipped";
        if (name.contains("cutout"))
            return "cutout";
        if (name.contains("translucent"))
            return "translucent";
        return "other";
    }
}
