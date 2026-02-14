package com.reibaru.blockcache.mixin;

import com.reibaru.blockcache.client.gpu.tile.TileManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "setBlock", at = @At("TAIL"))
private void blockcache$onBlockChanged(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
    System.out.println("[ClientLevelMixin] setBlock fired at " + pos);

    if (TileManager.INSTANCE != null) {
        TileManager.INSTANCE.onBlockChanged(pos, state);
    }
}
}
