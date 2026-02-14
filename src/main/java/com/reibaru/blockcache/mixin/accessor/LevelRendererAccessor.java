package com.reibaru.blockcache.mixin.accessor;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("renderChunkStorage")
    AtomicReference<?> blockcache$getRenderChunkStorage();
    
    @Accessor("renderChunks")
    ChunkRenderDispatcher.RenderChunk[] blockcache$getRenderChunks();
}


