package com.reibaru.blockcache.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.reibaru.blockcache.api.VertexBufferCacheApi;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VertexBuffer.class)

public abstract class VertexBufferUploadCancelMixin {

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void blockcache$cancelVanillaUpload(
            Object renderedBuffer,
            CallbackInfo ci) {
        BufferBuilder.RenderedBuffer buf = null;
        try {
            buf = (BufferBuilder.RenderedBuffer) renderedBuffer;
        } catch (ClassCastException ignored) {
        }

        VertexBufferCacheApi api = (VertexBufferCacheApi) (Object) this;

        if (api.blockcache$isUsingCachedMesh()) {
            System.out.println("[BlockCache] Cancelled vanilla VertexBuffer.upload()");
            ci.cancel();
        }
    }
}
