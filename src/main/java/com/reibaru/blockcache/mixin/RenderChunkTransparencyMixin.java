package com.reibaru.blockcache.mixin;


import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkRenderDispatcher.RenderChunk.class)

public class RenderChunkTransparencyMixin {

    @Inject(
        method = "resortTransparency(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void blockcache$onResortTransparency(
        Object renderType,
        Object dispatcher,
        CallbackInfoReturnable<Boolean> cir
    ) {
        // 実際の処理
    }
}
