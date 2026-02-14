package com.reibaru.blockcache.client.gpu.ray;

import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.reibaru.blockcache.client.gpu.pixel.TilePixel;

public class ScreenTileBuffer {

    private final int tilesX;
    private final int tilesY;
    private final int tileCount;

    private final ByteBuffer uploadBuffer;
    private final int ssboId;

    // SSBO のバインドポイント（block_render.frag と一致させる）
    public static final int BINDING_POINT = 0;

    public ScreenTileBuffer(int tilesX, int tilesY) {
        this.tilesX = tilesX;
        this.tilesY = tilesY;
        this.tileCount = tilesX * tilesY;

        int size = tileCount * TilePixel.BYTES;

        this.uploadBuffer = ByteBuffer
                .allocateDirect(size)
                .order(ByteOrder.nativeOrder());

        this.ssboId = GL43.glGenBuffers();
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
        GL43.glBufferData(
                GL43.GL_SHADER_STORAGE_BUFFER,
                size,
                GL43.GL_DYNAMIC_DRAW);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getTilesX() {
        return tilesX;
    }

    public int getTilesY() {
        return tilesY;
    }

    public int getTileCount() {
        return tileCount;
    }

    /**
     * TilePixel を SSBO 用バッファに書き込む
     */
    public void set(int index, TilePixel px) {
        int offset = index * TilePixel.BYTES;
        uploadBuffer.position(offset);
        px.writeTo(uploadBuffer);
    }

    public int getSsboId() {
        return ssboId;
    }

    /**
     * SSBO にアップロード
     */
    public void upload() {
        uploadBuffer.position(0);

        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
        GL43.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, uploadBuffer);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * レンダリング前に SSBO をバインドする
     */
    public void bind() {
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_POINT, ssboId);
    }
}
