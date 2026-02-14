package com.reibaru.blockcache.mixin.accessor;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.LinkedHashSet;

@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer$RenderChunkStorage")
public interface RenderChunkStorageAccessor {

    ChunkRenderDispatcher.RenderChunk blockcache$getChunkByPos(int chunkX, int chunkZ);

    @Accessor("renderChunks")
    LinkedHashSet<?> blockcache$getRenderChunks();
}

