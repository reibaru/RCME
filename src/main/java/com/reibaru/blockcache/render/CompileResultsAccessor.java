package com.reibaru.blockcache.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

public final class CompileResultsAccessor {

    private static final Class<?> CLS;
    private static final Field F_RENDERED_LAYERS;
    private static final Field F_VISIBILITY_SET;
    private static final Field F_TRANSPARENCY_STATE;
    private static final Field F_BLOCK_ENTITIES;
    private static final Field F_GLOBAL_BLOCK_ENTITIES;

    static {
        try {
            // RebuildTask.CompileResults のクラスを取得
            CLS = Class.forName(
                    "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask$CompileResults"
            );

            F_RENDERED_LAYERS = CLS.getDeclaredField("renderedLayers");
            F_VISIBILITY_SET = CLS.getDeclaredField("visibilitySet");
            F_TRANSPARENCY_STATE = CLS.getDeclaredField("transparencyState");
            F_BLOCK_ENTITIES = CLS.getDeclaredField("blockEntities");
            F_GLOBAL_BLOCK_ENTITIES = CLS.getDeclaredField("globalBlockEntities");

            F_RENDERED_LAYERS.setAccessible(true);
            F_VISIBILITY_SET.setAccessible(true);
            F_TRANSPARENCY_STATE.setAccessible(true);
            F_BLOCK_ENTITIES.setAccessible(true);
            F_GLOBAL_BLOCK_ENTITIES.setAccessible(true);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CompileResultsAccessor", e);
        }
    }

    private CompileResultsAccessor() {}

    // ---------------------------------------------------------
    // インスタンス生成
    // ---------------------------------------------------------

    public static Object newInstance() {
        try {
            Constructor<?> ctor = CLS.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create CompileResults instance", e);
        }
    }

    // ---------------------------------------------------------
    // renderedLayers の取得
    // ---------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<RenderType, BufferBuilder.RenderedBuffer> getRenderedLayers(Object results) {
        try {
            return (Map<RenderType, BufferBuilder.RenderedBuffer>) F_RENDERED_LAYERS.get(results);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get renderedLayers", e);
        }
    }

    // ---------------------------------------------------------
    // visibilitySet の設定
    // ---------------------------------------------------------

    public static void setVisibilitySet(Object results, Object visibilitySet) {
        try {
            F_VISIBILITY_SET.set(results, visibilitySet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set visibilitySet", e);
        }
    }

    // ---------------------------------------------------------
    // transparencyState の設定
    // ---------------------------------------------------------

    public static void setTransparencyState(Object results, Object state) {
        try {
            F_TRANSPARENCY_STATE.set(results, state);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set transparencyState", e);
        }
    }

    // ---------------------------------------------------------
    // BlockEntity の追加
    // ---------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static void addBlockEntity(Object results, BlockEntity be, boolean isGlobal) {
        try {
            if (isGlobal) {
                var list = (java.util.List<BlockEntity>) F_GLOBAL_BLOCK_ENTITIES.get(results);
                list.add(be);
            } else {
                var list = (java.util.List<BlockEntity>) F_BLOCK_ENTITIES.get(results);
                list.add(be);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to add BlockEntity", e);
        }
    }
}
