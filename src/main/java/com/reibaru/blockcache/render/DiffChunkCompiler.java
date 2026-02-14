package com.reibaru.blockcache.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.reibaru.blockcache.mixin.accessor.BlockRenderDispatcherAccessor;
import com.reibaru.blockcache.mixin.accessor.RenderChunkAccessor;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.Map;

public final class DiffChunkCompiler {

    private DiffChunkCompiler() {}

    public static Object compile(
        ChunkRenderDispatcher.RenderChunk chunk,
        IntOpenHashSet dirty,
        float camX,
        float camY,
        float camZ,
        ChunkBufferBuilderPack pack,
        RenderChunkRegion region   
) {
    Object results = CompileResultsAccessor.newInstance();
    VisGraph visgraph = new VisGraph();

    processDirtyBlocks(chunk, dirty, pack, visgraph, results, camX, camY, camZ, region);

    applyTransparencySorting(results, pack, chunk, camX, camY, camZ);
    finalizeRenderedLayers(results, pack);
    CompileResultsAccessor.setVisibilitySet(results, visgraph.resolve());

    return results;
}


    // -----------------------------
    // dirty ブロック処理
    // -----------------------------

private static void processDirtyBlocks(
        ChunkRenderDispatcher.RenderChunk chunk,
        IntOpenHashSet dirty,
        ChunkBufferBuilderPack pack,
        VisGraph visgraph,
        Object results,
        float camX,
        float camY,
        float camZ,
        RenderChunkRegion region
) {
    
    if (region == null) return;

    BlockPos origin = getOrigin(chunk);
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

    RandomSource random = RandomSource.create();

    for (int packed : dirty) {

        int lx = (packed >> 8) & 0xF;
        int lz = (packed >> 4) & 0xF;
        int ly = packed & 0xF;

        pos.set(origin.getX() + lx, origin.getY() + ly, origin.getZ() + lz);

        BlockState state = region.getBlockState(pos);

        //Model
        if (state.getRenderShape() == RenderShape.MODEL) { 
            renderModel(region, pos, state, pack, blockRenderer, random);
        }

        // VisGraph
        if (state.isSolidRender(region, pos)) {
            visgraph.setOpaque(pos);
        }

        // BlockEntity
        BlockEntity be = region.getBlockEntity(pos);
        if (be != null) {
            var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            var renderer = dispatcher.getRenderer(be);

            boolean isGlobal = false;
            CompileResultsAccessor.addBlockEntity(results, be, isGlobal);
        }

        // Fluid
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            renderFluid(region, pos, state, fluid, pack );
        }
    }
}


    // -----------------------------
    // Fluid 描画
    // -----------------------------

    private static void renderFluid(
        RenderChunkRegion region,
        BlockPos pos,
        BlockState state,
        FluidState fluid,
        ChunkBufferBuilderPack pack
) {
    LiquidBlockRenderer renderer =
        ((BlockRenderDispatcherAccessor) Minecraft.getInstance().getBlockRenderer())
                .blockcache$getLiquidBlockRenderer();


    BufferBuilder buf = pack.builder(RenderType.translucent());

    renderer.tesselate(
            region,      // BlockAndTintGetter
            pos,
            buf,         // VertexConsumer
            state,       // ★ Forge では BlockState が必要
            fluid
    );
}


    // -----------------------------
    // BlockModel 描画
    // -----------------------------

    private static void renderModel(
        RenderChunkRegion region,
        BlockPos pos,
        BlockState state,
        ChunkBufferBuilderPack pack,
        BlockRenderDispatcher blockRenderer,
        RandomSource random
) {
    PoseStack pose = new PoseStack();

    for (RenderType layer : RenderType.chunkBufferLayers()) {
        BufferBuilder buf = pack.builder(layer);
        if (buf == null) continue;

        blockRenderer.renderBatched(
            state,
            pos,
            region,
            pose,
            buf,
            false,
            random,
            ModelData.EMPTY,
            layer
        );
    }
}





    // -----------------------------
    // 透明ソート
    // -----------------------------

    private static void applyTransparencySorting(
            Object results,
            ChunkBufferBuilderPack pack,
            ChunkRenderDispatcher.RenderChunk chunk,
            float camX,
            float camY,
            float camZ
    ) {
        try {
            BufferBuilder buf = pack.builder(RenderType.translucent());
            if (buf == null || buf.isCurrentBatchEmpty()) return;

            // VertexSorting.BY_DISTANCE をリフレクションで取得
            Class<?> vsClass = Class.forName("net.minecraft.client.renderer.VertexSorting");
            Object byDistance = vsClass.getField("BY_DISTANCE").get(null);

            var method = BufferBuilder.class.getMethod("setQuadSorting", vsClass);
            method.invoke(buf, byDistance);

            Object sortState = buf.getSortState();
            CompileResultsAccessor.setTransparencyState(results, sortState);
        } catch (Exception e) {
            // ログはお好みで
        }
    }

    // -----------------------------
    // renderedLayers を埋める
    // -----------------------------

    private static void finalizeRenderedLayers(Object results, ChunkBufferBuilderPack pack) {
        Map<RenderType, RenderedBuffer> renderedLayers = CompileResultsAccessor.getRenderedLayers(results);

        for (RenderType layer : RenderType.chunkBufferLayers()) {
            BufferBuilder buf = pack.builder(layer);
            if (buf == null) continue;
            if (buf.isCurrentBatchEmpty()) continue;

            RenderedBuffer rendered = buf.endOrDiscardIfEmpty();
            if (rendered != null) {
                renderedLayers.put(layer, rendered);
            }
        }
    }

    // -----------------------------
    // RenderChunk から region/origin を取るヘルパー
    // （Accessor 経由で実装する想定）
    // -----------------------------


    private static BlockPos getOrigin(ChunkRenderDispatcher.RenderChunk chunk) {
       return ((RenderChunkAccessor)(Object)chunk).blockcache$getOrigin();
    }

}
