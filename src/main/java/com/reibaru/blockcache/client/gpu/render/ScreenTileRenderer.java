package com.reibaru.blockcache.client.gpu.render;

import com.reibaru.blockcache.client.gpu.ray.ScreenTileBuffer;
import org.lwjgl.opengl.GL30;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;

public class ScreenTileRenderer {

    public static final ScreenTileRenderer INSTANCE = new ScreenTileRenderer();

    private int vao;
    private int vbo;
    private int program;

    // uniform locations
    private int locTilesX;
    private int locTilesY;
    private int locTexAtlas;

    private int atlasTextureId = 0; // ← ずっと 0 のまま

    private ScreenTileRenderer() {
        initShader();
        initFullscreenQuad();
    }

    private void initShader() {
        String vsSrc = """
                #version 330 core
                layout(location = 0) in vec2 aPos;
                layout(location = 1) in vec2 aUV;
                out vec2 vUV;
                void main() {
                    vUV = aUV;
                    gl_Position = vec4(aPos, 0.0, 1.0);
                }
                """;

        String fsSrc = """
                #version 430 core

                // ===============================
                // SSBO: TilePixel 配列
                // ===============================
                struct TilePixel {
                    uint  texIndex;
                    float u;
                    float v;
                    float depth;
                };

                layout(std430, binding = 3) buffer TileData {
                    TilePixel pixels[];
                };

                // ===============================
                // Uniforms
                // ===============================
                uniform int uTilesX;
                uniform int uTilesY;
                uniform sampler2DArray texAtlas;

                in vec2 vUV;
                out vec4 fragColor;

                int getTileIndex() {
                    int tx = int(vUV.x * uTilesX);
                    int ty = int(vUV.y * uTilesY);

                    tx = clamp(tx, 0, uTilesX - 1);
                    ty = clamp(ty, 0, uTilesY - 1);

                    return ty * uTilesX + tx;
                }

                void main() {
                    int index = getTileIndex();
                    TilePixel px = pixels[index];

                    // 空タイル
                    if (px.texIndex == uint(-1)) {
                        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                        return;
                    }

                    fragColor = texture(texAtlas, vec3(px.u, px.v, float(px.texIndex)));
                }
                """;

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vsSrc);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("VS compile error: " + glGetShaderInfoLog(vs));
        }

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fsSrc);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("FS compile error: " + glGetShaderInfoLog(fs));
        }

        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Program link error: " + glGetProgramInfoLog(program));
        }

        glDeleteShader(vs);
        glDeleteShader(fs);

        // uniform locations
        locTilesX = glGetUniformLocation(program, "uTilesX");
        locTilesY = glGetUniformLocation(program, "uTilesY");
        locTexAtlas = glGetUniformLocation(program, "texAtlas");
    }

    private void initFullscreenQuad() {
        float[] verts = {
                // pos uv
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                1f, 1f, 1f, 1f,

                -1f, -1f, 0f, 0f,
                1f, 1f, 1f, 1f,
                -1f, 1f, 0f, 1f
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    public void render(ScreenTileBuffer buf) {

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        glUseProgram(program);

        // SSBO を binding=3 にバインド
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, buf.getSsboId());

        // タイル数を uniform で送る
        glUniform1i(locTilesX, buf.getTilesX());
        glUniform1i(locTilesY, buf.getTilesY());

        // テクスチャアトラス（sampler2DArray）をユニット0にバインド
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, atlasTextureId);
        glUniform1i(locTexAtlas, 0);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glUseProgram(0);

        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    // 後で TextureAtlas → GL_TEXTURE_2D_ARRAY を作ったらここにセットする想定
    public void setAtlasTextureId(int id) {
        this.atlasTextureId = id;
    }
}
