package com.reibaru.blockcache.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedHashSet;

import com.reibaru.blockcache.mixin.accessor.RenderChunkAccessor;

@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer$RenderChunkStorage")

public class RenderChunkStorageMixin {

    @Unique
    private final Long2ObjectMap<ChunkRenderDispatcher.RenderChunk> blockcache$chunkMap = new Long2ObjectOpenHashMap<>();

    @Shadow
    private LinkedHashSet<ChunkRenderDispatcher.RenderChunk> chunks;

    @Inject(method = "setChunks", at = @At("RETURN"))
    private void blockcache$onSetChunks(LinkedHashSet<ChunkRenderDispatcher.RenderChunk> newChunks, CallbackInfo ci) {
        blockcache$chunkMap.clear();
        for (ChunkRenderDispatcher.RenderChunk c : newChunks) {
            BlockPos origin = ((RenderChunkAccessor) (Object) c).blockcache$getOrigin();
            int chunkX = origin.getX();
            int chunkZ = origin.getZ();
            long key = blockcache$packChunkPos(chunkX, chunkZ);
            blockcache$chunkMap.put(key, c);
        }
    }

    @Unique
    public ChunkRenderDispatcher.RenderChunk blockcache$getChunkByPos(int chunkX, int chunkZ) {
        long key = blockcache$packChunkPos(chunkX, chunkZ);
        return blockcache$chunkMap.get(key);
    }

    @Unique
    private static long blockcache$packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) << 32 | ((long) chunkZ & 0xFFFFFFFFL);
    }
}
