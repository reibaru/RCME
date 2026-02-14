package com.reibaru.blockcache.events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;


@Mod.EventBusSubscriber(modid = "blockcache", value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // サーバー側イベントも飛んでくるので、クライアントだけに限定
        if (!(event.getLevel() instanceof ClientLevel)) {
            return;
        }

        BlockPos pos = event.getPos();
        Minecraft mc = Minecraft.getInstance();
        LevelRenderer renderer = mc.levelRenderer;

        // BlockPos → セクション座標へ変換（16x16x16 単位）
        int sx = pos.getX() >> 4;
        int sy = pos.getY() >> 4;
        int sz = pos.getZ() >> 4;

        System.out.println("[BlockCache][DEBUG] BlockBreak at " + pos +
                " -> section=(" + sx + "," + sy + "," + sz + "), marking dirty");

        renderer.setSectionDirtyWithNeighbors(sx, sy, sz);
    }
}
