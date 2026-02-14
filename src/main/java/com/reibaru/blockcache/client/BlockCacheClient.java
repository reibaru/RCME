package com.reibaru.blockcache.client;

import com.mojang.logging.LogUtils;
import com.reibaru.blockcache.BlockCacheMod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;


@Mod.EventBusSubscriber(
        modid = BlockCacheMod.MODID,
        value = Dist.CLIENT
)
public class BlockCacheClient {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static boolean logged = false;

        @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;

        //起動確認ログ
          if (!logged) {
               logged = true;
            BlockCacheMod.LOGGER.info("[BlockCache] Client tick is running!");
        }

    }
}
