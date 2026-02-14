package com.reibaru.blockcache.client.event;

import com.reibaru.blockcache.client.gpu.ray.ScreenTileBuffer;
import com.reibaru.blockcache.client.gpu.ray.TileRaycaster;
import com.reibaru.blockcache.client.gpu.render.ScreenTileRenderer;

import com.reibaru.blockcache.client.gpu.render.TileRenderer;
import com.reibaru.blockcache.client.gpu.tile.TileManager;
import com.reibaru.blockcache.client.gpu.buffer.TileBuffer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "blockcache", value = Dist.CLIENT)
public class ClientRenderEvents {

    private static ScreenTileBuffer SCREEN_BUF = null;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {

        // 初回だけ OpenGL がある状態で生成する
        if (SCREEN_BUF == null) {
            SCREEN_BUF = new ScreenTileBuffer(32, 18);
        }

        // ① タイルレイキャスト（レイキャスト版）
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            TileRaycaster.INSTANCE.update(SCREEN_BUF);
            SCREEN_BUF.upload();
        }

        // ② GPUメッシュ描画（TileRenderer）
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {

            var tiles = TileManager.INSTANCE.getAllTiles();

            if (!tiles.isEmpty()) {
                TileBuffer first = tiles.iterator().next();
                TileRenderer.INSTANCE.renderTile(first);
            }

            // ★ 全タイル描画したい場合（後で戻す）
            /*
             * for (TileBuffer tile : tiles) {
             * TileRenderer.INSTANCE.renderTile(tile);
             * }
             */
        }

        // ③ フルスクリーン描画（レイキャスト版）
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            //ScreenTileRenderer.INSTANCE.render(SCREEN_BUF);
        }
    }
}
