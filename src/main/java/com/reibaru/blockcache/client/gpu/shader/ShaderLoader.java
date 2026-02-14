package com.reibaru.blockcache.client.gpu.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ShaderLoader {

    // ───────────────────────────────────────────────
    // Compute Shader
    // ───────────────────────────────────────────────
    public static String loadComputeShader(String path) {
        return loadShader(path);
    }

    // ───────────────────────────────────────────────
    // Vertex Shader
    // ───────────────────────────────────────────────
    public static String loadVertexShader(String path) {
        return loadShader(path);
    }

    // ───────────────────────────────────────────────
    // Fragment Shader
    // ───────────────────────────────────────────────
    public static String loadFragmentShader(String path) {
        return loadShader(path);
    }

    // ───────────────────────────────────────────────
    // 共通の読み込み処理
    // ───────────────────────────────────────────────
    private static String loadShader(String path) {
        try {
            ResourceLocation rl = new ResourceLocation(path);

            Resource resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(rl)
                    .orElseThrow(() -> new RuntimeException("Shader not found: " + path));

            return new String(resource.open().readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + path, e);
        }
    }
}
