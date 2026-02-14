package com.reibaru.blockcache.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = BufferBuilder.class)
public interface BufferBuilderInjectInvoker {

    @Invoker("storeRenderedBuffer")
    BufferBuilder.RenderedBuffer blockcache$storeRenderedBuffer();
}
