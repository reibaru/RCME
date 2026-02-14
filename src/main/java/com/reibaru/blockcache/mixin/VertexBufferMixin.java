package com.reibaru.blockcache.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reibaru.blockcache.CachedMesh;
import com.reibaru.blockcache.api.VertexBufferCacheApi; // ← これに変更！

import org.lwjgl.opengl.GL15C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

@Mixin(value = VertexBuffer.class)

public abstract class VertexBufferMixin implements VertexBufferCacheApi {

    // ---- Shadow ----
    @Shadow private VertexBuffer.Usage usage;
    @Shadow private int vertexBufferId;
    @Shadow private int indexBufferId;
    @Shadow private int arrayObjectId;

    @Shadow @Nullable private VertexFormat format;
    @Shadow @Nullable private RenderSystem.AutoStorageIndexBuffer sequentialIndices;

    @Shadow private VertexFormat.IndexType indexType;
    @Shadow private int indexCount;
    @Shadow private VertexFormat.Mode mode;

    @Shadow public abstract boolean isInvalid();

    // ---- 独自 API の実装 ----

    @Unique private boolean blockcache$usingCachedMesh = false;

    @Override
    public boolean blockcache$isUsingCachedMesh() {
        return this.blockcache$usingCachedMesh;
    }

    @Override
    public void blockcache$setUsingCachedMesh(boolean value) {
        this.blockcache$usingCachedMesh = value;
    }

    @Override
public void blockcache$uploadFromCachedMesh(CachedMesh mesh) {

    if (this.isInvalid()) {
        System.out.println("[BlockCache] VertexBuffer is invalid, cannot upload cached mesh.");
        return;
    }

    System.out.println("[BlockCache] uploadFromCachedMesh CALLED (with recordRenderCall)");

    this.format = mesh.format;
    this.mode = mesh.mode;
    this.indexType = mesh.indexType;
    this.indexCount = mesh.indexCount;

    RenderSystem.recordRenderCall(() -> {

        mesh.vertexBuffer.rewind();
        mesh.indexBuffer.rewind();

        boolean firstUpload = !this.blockcache$usingCachedMesh;

        // --- Vertex buffer ---
        GlStateManager._glBindBuffer(GL15C.GL_ARRAY_BUFFER, this.vertexBufferId);
        if (firstUpload) {
            GlStateManager._glBufferData(
                    GL15C.GL_ARRAY_BUFFER,
                    mesh.vertexBuffer,
                    GL15C.GL_STATIC_DRAW
            );
        } else {
            GL15C.glBufferSubData(
                    GL15C.GL_ARRAY_BUFFER,
                    0,
                    mesh.vertexBuffer
            );
        }

        // --- Index buffer ---
        GlStateManager._glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, this.indexBufferId);
        if (firstUpload) {
            GlStateManager._glBufferData(
                    GL15C.GL_ELEMENT_ARRAY_BUFFER,
                    mesh.indexBuffer,
                    GL15C.GL_STATIC_DRAW
            );
        } else {
            GL15C.glBufferSubData(
                    GL15C.GL_ELEMENT_ARRAY_BUFFER,
                    0,
                    mesh.indexBuffer
            );
        }

        // --- VAO セットアップは初回だけ ---
        if (firstUpload) {
            GlStateManager._glBindVertexArray(this.arrayObjectId);
            this.format.setupBufferState();
            GlStateManager._glBindVertexArray(0);
        }

        GlStateManager._glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        GlStateManager._glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, 0);

        this.blockcache$setUsingCachedMesh(true);
    });
}

}
