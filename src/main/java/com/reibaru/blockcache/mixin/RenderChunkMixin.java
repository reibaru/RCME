package com.reibaru.blockcache.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.reibaru.blockcache.CacheManager;
import com.reibaru.blockcache.api.DirtyBlockHolder;
import com.reibaru.blockcache.api.VertexBufferCacheApi;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import com.reibaru.blockcache.CachedMesh;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.CompiledChunk;
import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = ChunkRenderDispatcher.RenderChunk.class)

public class RenderChunkMixin implements DirtyBlockHolder {

    @Unique
    private final IntOpenHashSet blockcache$dirtyBlocks = new IntOpenHashSet();

    @Override
    public IntOpenHashSet blockcache$getDirtyBlocks() {
        return blockcache$dirtyBlocks;
    }

    @Override
    public void blockcache$clearDirtyBlocks() {
        blockcache$dirtyBlocks.clear();
    }

    // =====================================================
    // ① 自前で持つチャンク座標（コンストラクタから受け取る）
    // =====================================================
    @Unique
    private int blockcache$chunkX;

    @Unique
    private int blockcache$chunkZ;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void blockcache$onInit(ChunkRenderDispatcher dispatcher,
            int index,
            int chunkX,
            int chunkY,
            int chunkZ,
            CallbackInfo ci) {
        this.blockcache$chunkX = chunkX;
        this.blockcache$chunkZ = chunkZ;

        System.out.println("[BlockCache][MixinTest] RenderChunkMixin INIT: chunkX=" + chunkX + " chunkZ=" + chunkZ);
    }

    @Unique
    public int blockcache$getChunkX() {
        return this.blockcache$chunkX;
    }

    @Unique
    public int blockcache$getChunkZ() {
        return this.blockcache$chunkZ;
    }

    // =====================================================
    // ② キャッシュ状態フラグ（後で Rebuild 抑制に使う）
    // =====================================================
    @Unique
    private boolean blockcache$hasCachedTranslucent = false;

    @Unique
    public void blockcache$setHasCachedTranslucent(boolean value) {
        this.blockcache$hasCachedTranslucent = value;
    }

    @Unique
    public boolean blockcache$hasCachedTranslucent() {
        return this.blockcache$hasCachedTranslucent;
    }

    
    
    @Unique
    public void blockcache$debugLoadCachedLayer(RenderType layer) {
        int chunkX = blockcache$getChunkX();
        int chunkZ = blockcache$getChunkZ();

        String layerName = blockcache$safeRenderType(layer);

        Path cacheDir = FMLPaths.GAMEDIR.get().resolve("blockcache/cache");
        String fileName = "chunk_" + chunkX + "_" + chunkZ + "_" + layerName + ".bin";
        Path file = cacheDir.resolve(fileName);

        if (!Files.exists(file)) {
            System.out.println("[BlockCache] no cache for " + fileName);
            return;
        }

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {

            ByteBuffer sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

            // vbSize
            sizeBuf.clear();
            channel.read(sizeBuf);
            sizeBuf.flip();
            int vbSize = sizeBuf.getInt();

            ByteBuffer vb = null;
            if (vbSize > 0) {
                vb = ByteBuffer.allocateDirect(vbSize).order(ByteOrder.LITTLE_ENDIAN);
                channel.read(vb);
                vb.flip();
            }

            // ibSize
            sizeBuf.clear();
            channel.read(sizeBuf);
            sizeBuf.flip();
            int ibSize = sizeBuf.getInt();

            ByteBuffer ib = null;
            if (ibSize > 0) {
                ib = ByteBuffer.allocateDirect(ibSize).order(ByteOrder.LITTLE_ENDIAN);
                channel.read(ib);
                ib.flip();
            }

            System.out.println("[BlockCache] loaded cache for " + fileName +
                    " vb=" + vbSize + " bytes, ib=" + ibSize + " bytes");

        } catch (Exception e) {
            System.out.println("[BlockCache] error reading cache for " + fileName);
            e.printStackTrace();
        }
    }

    @Unique
    private String blockcache$safeRenderType(RenderType type) {
        if (type == RenderType.solid())
            return "solid";
        if (type == RenderType.cutout())
            return "cutout";
        if (type == RenderType.cutoutMipped())
            return "cutout_mipped";
        if (type == RenderType.translucent())
            return "translucent";
        return "other";
    }

    // =====================================================
    // キャッシュ存在チェック
    // =====================================================
    @Unique
    private boolean blockcache$hasCachedMesh() {
        return CacheManager.hasCachedMesh(blockcache$getChunkX(), blockcache$getChunkZ());
    }

    // =====================================================
    // setDirty フック（キャッシュがあれば再生成スキップ）
    // =====================================================
    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void blockcache$onSetDirty(boolean p_112833_, CallbackInfo ci) {
        if (blockcache$hasCachedMesh()) {
            System.out.println("[BlockCache] RenderChunk.setDirty cancelled due to cached mesh");
            ci.cancel(); // キャッシュがあれば完全にスキップ
        }
    }

    // =====================================================
    // キャッシュ適用（GPU メッシュ復元）
    // =====================================================
    @Shadow
    private Map<RenderType, VertexBuffer> buffers;
    @Shadow
    private AtomicReference<CompiledChunk> compiled;

    @Unique
    private void blockcache$applyCachedMesh() {

        Map<RenderType, CachedMesh> map = CacheManager.getCachedMeshes(blockcache$getChunkX(), blockcache$getChunkZ());

        if (map == null || map.isEmpty())
            return;

        ChunkRenderDispatcher.CompiledChunk newCompiled = new ChunkRenderDispatcher.CompiledChunk();

        for (Map.Entry<RenderType, CachedMesh> e : map.entrySet()) {
            RenderType layer = e.getKey();
            CachedMesh mesh = e.getValue();

            VertexBuffer vb = this.buffers.get(layer);
            if (vb != null) {
                ((VertexBufferCacheApi) (Object) vb).blockcache$uploadFromCachedMesh(mesh);
            }
        }

        this.compiled.set(newCompiled);
    }

    // =====================================================
    // rebuildChunk をキャッシュで置き換える
    // =====================================================
    @Inject(method = "rebuildChunkAsync", at = @At("HEAD"), cancellable = true)
    private void blockcache$onRebuildChunk(CallbackInfo ci) {
        if (blockcache$hasCachedMesh()) {
            blockcache$applyCachedMesh();
            ci.cancel(); // rebuildChunk を完全にスキップ
        }
    }

}
