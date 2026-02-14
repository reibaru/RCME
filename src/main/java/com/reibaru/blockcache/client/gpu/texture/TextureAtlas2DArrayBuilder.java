package com.reibaru.blockcache.client.gpu.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.ArrayList;

public class TextureAtlas2DArrayBuilder {

    private final Minecraft mc = Minecraft.getInstance();

    public int buildTextureArray(List<TextureAtlasSprite> sprites) {

        if (sprites.isEmpty()) {
            throw new IllegalStateException("No sprites provided to TextureAtlas2DArrayBuilder!");
        }

        // ============================
        // まず 16×16 のスプライトだけに絞る
        // ============================
        List<TextureAtlasSprite> filtered = sprites.stream()
                .filter(s -> {
                    NativeImage img = s.contents().getOriginalImage();
                    return img != null && img.getWidth() == 16 && img.getHeight() == 16;
                })
                .toList();

        if (filtered.isEmpty()) {
            throw new IllegalStateException("No 16x16 sprites found for TextureAtlas2DArrayBuilder!");
        }

        // 複数サイズのスプライトが混在する可能性があるため、
        // サイズごとにグループ化して最も多いサイズグループのみを使う
        Map<String, List<TextureAtlasSprite>> groups = new HashMap<>();
        for (TextureAtlasSprite s : filtered) {
            NativeImage img = s.contents().getOriginalImage();
            if (img == null)
                continue;
            String key = img.getWidth() + "x" + img.getHeight();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<TextureAtlasSprite> chosen = filtered;
        if (!groups.isEmpty()) {
            chosen = groups.values().stream().max(Comparator.comparingInt(List::size)).orElse(filtered);
        }

        NativeImage firstImg = chosen.get(0).contents().getOriginalImage();
        int width = firstImg.getWidth();
        int height = firstImg.getHeight();
        int layers = chosen.size();

        // ============================
        // GL_TEXTURE_2D_ARRAY を生成
        // ============================
        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, texId);

        // 空の 3D テクスチャを確保
        GL12.glTexImage3D(
                GL30.GL_TEXTURE_2D_ARRAY,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                layers,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                0);

        // ============================
        // 各スプライトをレイヤーにコピー
        // ============================
        for (int i = 0; i < layers; i++) {
            TextureAtlasSprite sprite = chosen.get(i);
            NativeImage img = sprite.contents().getOriginalImage();
            if (img == null)
                continue;

            int w = img.getWidth();
            int h = img.getHeight();

            if (w != width || h != height) {
                throw new IllegalStateException("Sprite size mismatch in TextureAtlas2DArrayBuilder: "
                        + w + "x" + h + " vs " + width + "x" + height);
            }

            ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);

            // NativeImage は stride があるので getPixelRGBA で読む
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = img.getPixelRGBA(x, y);

                    byte r = (byte) ((argb >> 16) & 0xFF);
                    byte g = (byte) ((argb >> 8) & 0xFF);
                    byte b = (byte) (argb & 0xFF);
                    byte a = (byte) ((argb >> 24) & 0xFF);

                    buf.put(r).put(g).put(b).put(a);
                }
            }
            buf.flip();

            GL12.glTexSubImage3D(
                    GL30.GL_TEXTURE_2D_ARRAY,
                    0,
                    0, 0, i,
                    width,
                    height,
                    1,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    buf);
        }

        // ============================
        // テクスチャパラメータ
        // ============================
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);

        return texId;
    }
}
