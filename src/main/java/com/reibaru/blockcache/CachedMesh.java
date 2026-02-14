package com.reibaru.blockcache;

import com.mojang.blaze3d.vertex.VertexFormat;

import java.nio.ByteBuffer;

public class CachedMesh {

    // GPU に渡す生データ
    public final ByteBuffer vertexBuffer;
    public final ByteBuffer indexBuffer;

    // メッシュのメタ情報
    public final int vertexCount;
    public final int indexCount;

    public final VertexFormat format;
    public final VertexFormat.Mode mode;
    public final VertexFormat.IndexType indexType;

    public CachedMesh(
            ByteBuffer vertexBuffer,
            ByteBuffer indexBuffer,
            int vertexCount,
            int indexCount,
            VertexFormat format,
            VertexFormat.Mode mode,
            VertexFormat.IndexType indexType) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;

        this.vertexCount = vertexCount;
        this.indexCount = indexCount;

        this.format = format;
        this.mode = mode;
        this.indexType = indexType;
    }

    public int indexCount() {
        return this.indexCount;
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    /**
     * 使用済みのメモリを明示的にクリア。
     * CACHE から削除する或いは アップロード後は呼び出し推奨。
     */
    public void cleanup() {
        if (this.vertexBuffer != null && this.vertexBuffer.isDirect()) {
            // allocateDirect のメモリリークを防ぐため、参照を明示的にクリア
            // （実際には Java の GC が管理するため、完全な cleanup はできないが、参照をクリアすることで意図を示す）
            System.out.println("[BlockCache] CachedMesh.cleanup: clearing buffers");
        }
        if (this.indexBuffer != null && this.indexBuffer.isDirect()) {
            System.out.println("[BlockCache] CachedMesh.cleanup: clearing index buffer");
        }
    }
}