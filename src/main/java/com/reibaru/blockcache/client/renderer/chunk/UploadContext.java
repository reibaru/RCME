package com.reibaru.blockcache.client.renderer.chunk;

import java.util.WeakHashMap;
import java.util.Map;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ChunkPos;

public final class UploadContext {
    public record LayerKey(ChunkPos pos, String layerName) {}

    private static final Map<VertexBuffer, LayerKey> VBO_TO_LAYER = new WeakHashMap<>();

    public static void put(VertexBuffer vbo, ChunkPos pos, RenderType type) {
        if (vbo == null || pos == null || type == null) return;
        VBO_TO_LAYER.put(vbo, new LayerKey(pos, type.toString()));
    }

    public static LayerKey get(VertexBuffer vbo) {
        return VBO_TO_LAYER.get(vbo);
    }
}
