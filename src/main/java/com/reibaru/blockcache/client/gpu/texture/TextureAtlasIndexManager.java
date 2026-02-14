package com.reibaru.blockcache.client.gpu.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class TextureAtlasIndexManager {

    // キーは ResourceLocation（スプライト名）
    private final Map<ResourceLocation, Integer> spriteToIndex = new HashMap<>();
    // 実際に 2DArray に詰めるのは TextureAtlasSprite
    private final List<TextureAtlasSprite> indexToSprite = new ArrayList<>();

    /**
     * スプライトに対応するレイヤー番号を返す。
     * 未登録なら新規に割り当てる。
     */
    public int getIndex(TextureAtlasSprite sprite) {
        ResourceLocation id = sprite.contents().name();
        return spriteToIndex.computeIfAbsent(id, key -> {
            int index = indexToSprite.size();
            indexToSprite.add(sprite);
            return index;
        });
    }

    /**
     * レイヤー番号からスプライトを取得する。
     */
    public TextureAtlasSprite getSprite(int index) {
        return indexToSprite.get(index);
    }

    /**
     * 登録されているスプライト数（＝レイヤー数）
     */
    public int size() {
        return indexToSprite.size();
    }

    /**
     * すべてのスプライトを返す（2DArray テクスチャ構築用）
     */
    public List<TextureAtlasSprite> getAllSprites() {
        return List.copyOf(indexToSprite);
    }

    /**
     * スプライトが登録済みかどうか
     */
    public boolean contains(TextureAtlasSprite sprite) {
        return spriteToIndex.containsKey(sprite.contents().name());
    }

    /**
     * 全データをクリア（必要なら）
     */
    public void clear() {
        spriteToIndex.clear();
        indexToSprite.clear();
    }
}
