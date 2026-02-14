package com.reibaru.blockcache.mixin;

import com.reibaru.blockcache.BlockCacheState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$ResortTransparencyTask"
)
public class ResortTransparencyTaskMixin {

    @Inject(method = "run",at = @At("HEAD"), cancellable = true)
    private void blockcache$skipSort(CallbackInfo ci) {
        // 視点がほぼ変わっていない → ソート不要
        if (!BlockCacheState.angleChangedEnough()) {
            ci.cancel();
        }
    }
}
