package com.reibaru.blockcache.client.event;

import com.reibaru.blockcache.client.gpu.tile.TileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "blockcache", value = Dist.CLIENT)
public class ClientLevelEvents {

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
    if (event.getLevel().isClientSide()) {
        Level level = (Level) event.getLevel(); // ★ キャスト
        TileManager.INSTANCE = new TileManager(level);
        System.out.println("[BlockCache] TileManager initialized!");
    }
}

}
