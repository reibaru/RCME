package com.reibaru.blockcache.client.gpu.pixel;

import java.nio.ByteBuffer;

public class TilePixel {

    // 1 TilePixel = float4 = 16 bytes
    public static final int BYTES = 4 * Float.BYTES;

    public float texIndex;
    public float u;
    public float v;
    public float depth;

    public TilePixel() {
        this.texIndex = -1f;
        this.u = 0f;
        this.v = 0f;
        this.depth = 1f;
    }

    public TilePixel(float texIndex, float u, float v, float depth) {
        this.texIndex = texIndex;
        this.u = u;
        this.v = v;
        this.depth = depth;
    }

    /**
     * SSBO 用 ByteBuffer に書き込む
     */
    public void writeTo(ByteBuffer buf) {
        buf.putFloat(texIndex);
        buf.putFloat(u);
        buf.putFloat(v);
        buf.putFloat(depth);
    }
}
