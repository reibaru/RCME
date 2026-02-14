package com.reibaru.blockcache.mixin.accessor;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = ChunkRenderDispatcher.RenderChunk.class)
public interface RenderChunkAccessor {

    // blockPos origin フィールドにアクセス（MutableBlockPos ではなく BlockPos）
    @Accessor("origin")
    BlockPos blockcache$getOrigin();

    @Accessor("buffers")
    Map<RenderType, VertexBuffer> blockcache$getBuffers();

    @Accessor("compiled")
    AtomicReference<ChunkRenderDispatcher.CompiledChunk> blockcache$getCompiledRef();

    @Invoker("beginLayer")
    void blockcache$beginLayer(BufferBuilder builder);
}
