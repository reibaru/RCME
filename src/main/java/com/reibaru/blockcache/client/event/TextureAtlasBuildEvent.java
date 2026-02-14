package com.reibaru.blockcache.client.event;

import com.reibaru.blockcache.client.gpu.texture.TextureAtlas2DArrayBuilder;
import com.reibaru.blockcache.client.gpu.texture.TextureAtlasIndexManager;
import com.reibaru.blockcache.client.gpu.ray.TileRaycaster;
import com.reibaru.blockcache.client.gpu.render.ScreenTileRenderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite; // ★ これが必須
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "blockcache", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TextureAtlasBuildEvent {

    private static final TextureAtlasIndexManager INDEX_MANAGER = new TextureAtlasIndexManager();

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {

        TextureAtlas atlas = event.getAtlas();

        if (!atlas.location().equals(TextureAtlas.LOCATION_BLOCKS)) {
            return;
        }

        // まず IndexManager をクリア
        INDEX_MANAGER.clear();

        // すべてのスプライトを IndexManager に登録
        for (ResourceLocation rl : atlas.getTextureLocations()) {
            TextureAtlasSprite sprite = atlas.getSprite(rl);
            if (sprite != null) {
                INDEX_MANAGER.getIndex(sprite); // 登録だけ
            }
        }

        // ★ builder に渡すのは IndexManager の順番
        List<TextureAtlasSprite> sprites = INDEX_MANAGER.getAllSprites();

        TextureAtlas2DArrayBuilder builder = new TextureAtlas2DArrayBuilder();
        int texId = builder.buildTextureArray(sprites);

        ScreenTileRenderer.INSTANCE.setAtlasTextureId(texId);
        TileRaycaster.INSTANCE.setAtlasIndexManager(INDEX_MANAGER);


        System.out.println("[BlockCache] Built 2DArray texture with " + sprites.size() + " layers.");
    }


    public static TextureAtlasIndexManager getIndexManager() {
        return INDEX_MANAGER;
    }
}
