package com.reibaru.blockcache.client.gpu.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reibaru.blockcache.client.gpu.buffer.TileBuffer;
import com.reibaru.blockcache.client.gpu.shader.ShaderLoader;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;

public class TileRenderer {

    public static final TileRenderer INSTANCE = new TileRenderer();

    private int computeProgram;
    private int renderProgram;

    private int vertexBufferId;
    private int atomicCounterId;
    private int vaoId;

    private final java.nio.ByteBuffer counterBuf = java.nio.ByteBuffer.allocateDirect(4)
            .order(java.nio.ByteOrder.nativeOrder());

    private int locTileOrigin;

    private TileRenderer() {
        initComputeShader();
        initRenderShader();
        initBuffers();
    }

    // ───────────────────────────────────────────────
    // Compute Shader のロード
    // ───────────────────────────────────────────────
    private void initComputeShader() {
        computeProgram = glCreateProgram();

        int shader = glCreateShader(GL_COMPUTE_SHADER);
        String source = ShaderLoader.loadComputeShader("blockcache:shaders/tile_renderer/block_render.comp");

        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Compute shader compile error: " + glGetShaderInfoLog(shader));
        }

        glAttachShader(computeProgram, shader);
        glLinkProgram(computeProgram);

        if (glGetProgrami(computeProgram, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Compute shader link error: " + glGetProgramInfoLog(computeProgram));
        }

        glDeleteShader(shader);

        locTileOrigin = glGetUniformLocation(computeProgram, "uTileWorldOrigin");
    }

    // ───────────────────────────────────────────────
    // Render Shader のロード（.vert / .frag）
    // ───────────────────────────────────────────────
    private void initRenderShader() {
        // ★ メッシュ用シェーダーを読むように変更
        int vert = glCreateShader(GL_VERTEX_SHADER);
        String vertSrc = ShaderLoader.loadVertexShader("blockcache:shaders/tile_renderer/block_mesh.vert");
        glShaderSource(vert, vertSrc);
        glCompileShader(vert);
        if (glGetShaderi(vert, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Vertex shader compile error: " + glGetShaderInfoLog(vert));
        }

        int frag = glCreateShader(GL_FRAGMENT_SHADER);
        String fragSrc = ShaderLoader.loadFragmentShader("blockcache:shaders/tile_renderer/block_mesh.frag");
        glShaderSource(frag, fragSrc);
        glCompileShader(frag);
        if (glGetShaderi(frag, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Fragment shader compile error: " + glGetShaderInfoLog(frag));
        }

        renderProgram = glCreateProgram();
        glAttachShader(renderProgram, vert);
        glAttachShader(renderProgram, frag);
        glLinkProgram(renderProgram);
        if (glGetProgrami(renderProgram, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Render program link error: " + glGetProgramInfoLog(renderProgram));
        }

        glDeleteShader(vert);
        glDeleteShader(frag);
    }

    // ───────────────────────────────────────────────
    // SSBO / Atomic Counter / VAO の初期化
    // ───────────────────────────────────────────────
    private void initBuffers() {

        // SSBO: 頂点出力 (16MB)
        vertexBufferId = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, vertexBufferId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, 1024 * 1024 * 16, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        // Atomic Counter
        atomicCounterId = glGenBuffers();
        glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, atomicCounterId);
        glBufferData(GL_ATOMIC_COUNTER_BUFFER, 4, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, 0);

        // VAO
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        glBindBuffer(GL_ARRAY_BUFFER, vertexBufferId);

        // vec4 × 3 = 48 bytes per vertex
        // pos (vec4)
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 48, 0);
        glEnableVertexAttribArray(0);

        // normal (vec4)
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 48, 16);
        glEnableVertexAttribArray(1);

        // color (vec4)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 48, 32);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }

    // ───────────────────────────────────────────────
    // タイル描画
    // ───────────────────────────────────────────────
    public void renderTile(TileBuffer tile) {

        // --- Compute: 頂点生成 ---
        glUseProgram(computeProgram);

        // SSBO: TileBuffer
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, tile.getSsboId());

        // SSBO: VertexBuffer
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, vertexBufferId);

        // Atomic Counter
        glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER, 5, atomicCounterId);

        // Atomic Counter を 0 にリセット
        glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, atomicCounterId);
        glClearBufferSubData(
                GL_ATOMIC_COUNTER_BUFFER,
                GL_R32UI,
                0, 4,
                GL_RED_INTEGER,
                GL_UNSIGNED_INT,
                (java.nio.ByteBuffer) null);

        // uniform: タイル原点
        glUniform3i(locTileOrigin, tile.worldX(), tile.worldY(), tile.worldZ());

        // Compute Shader 実行
        glDispatchCompute(4, 4, 4);

        // SSBO → Vertex Shader への同期
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);

        glUseProgram(0);

        // --- 頂点数を取得 ---
        glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, atomicCounterId);
        int vertexCount = readAtomicCounter();
        glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, 0);

        if (vertexCount == 0)
            return;

        // --- 描画 ---
        glUseProgram(renderProgram);

        // 深度テスト & カリング
        glDisable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glDisable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Minecraft
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();

        // --- Projection 行列（Mojang → JOML 変換） ---
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());

        // --- View 行列 ---
        Matrix4f view = new Matrix4f();
        view.rotation(cam.rotation());
        view.translate(
                (float) -cam.getPosition().x,
                (float) -cam.getPosition().y,
                (float) -cam.getPosition().z);

        // --- Model 行列（identity） ---
        Matrix4f model = new Matrix4f();

        // --- uniform に渡す ---
        int locProj = glGetUniformLocation(renderProgram, "uProjection");
        int locView = glGetUniformLocation(renderProgram, "uView");
        int locModel = glGetUniformLocation(renderProgram, "uModel");

        float[] projArr = new float[16];
        float[] viewArr = new float[16];
        float[] modelArr = new float[16];

        proj.get(projArr);
        view.get(viewArr);
        model.get(modelArr);

        glUniformMatrix4fv(locProj, false, projArr);
        glUniformMatrix4fv(locView, false, viewArr);
        glUniformMatrix4fv(locModel, false, modelArr);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);

        glUseProgram(0);
    }

    private int readAtomicCounter() {
        counterBuf.clear();
        glGetBufferSubData(GL_ATOMIC_COUNTER_BUFFER, 0, counterBuf);
        return counterBuf.getInt(0);
    }
}
