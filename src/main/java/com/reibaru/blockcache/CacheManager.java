package com.reibaru.blockcache;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    public static final Map<Long, Map<RenderType, CachedMesh>> CACHE = new ConcurrentHashMap<>();

    // =====================================================
    // キャッシュ読み込み
    // =====================================================
    public static void loadCache() {
        Path cacheDir = FMLPaths.GAMEDIR.get().resolve("blockcache/cache");

        System.out.println("[BlockCache] loadCache: scanning cache directory=" + cacheDir);

        if (!Files.exists(cacheDir)) {
            System.out.println("[BlockCache] No cache directory found.");
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheDir, "*.bin")) {
            for (Path file : stream) {
                loadSingleFile(file);
            }
        } catch (IOException e) {
            System.out.println("[BlockCache] Error while loading cache directory: " + cacheDir);
            e.printStackTrace();
        }

        System.out.println("[BlockCache] Loaded cache entries: " + CACHE.size());
    }

    private static RenderType toRenderType(String name) {
        return switch (name) {
            case "solid" -> RenderType.solid();
            case "cutout" -> RenderType.cutout();
            case "cutout_mipped" -> RenderType.cutoutMipped();
            case "translucent" -> RenderType.translucent();
            default -> null;
        };
    }

    public static boolean hasCachedMesh(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        Map<RenderType, CachedMesh> map = CACHE.get(key);
        return map != null && !map.isEmpty();
    }

    public static Map<RenderType, CachedMesh> getCachedMeshes(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        return CACHE.get(key);
    }

    public static void putCachedMesh(int chunkX, int chunkZ, RenderType layer, CachedMesh mesh) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        CACHE.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(layer, mesh);
    }

    // =====================================================
    // チャンク単位で読み込み
    // =====================================================
    public static Map<RenderType, CachedMesh> loadChunk(ChunkPos pos) {
        Map<RenderType, CachedMesh> result = new ConcurrentHashMap<>();
        Path cacheDir = FMLPaths.GAMEDIR.get().resolve("blockcache/cache");

        String[] layers = { "solid", "cutout", "cutout_mipped", "translucent" };

        for (String layer : layers) {
            String fileName = String.format("chunk_%d_%d_%s.bin", pos.x, pos.z, layer);
            Path filePath = cacheDir.resolve(fileName);

            if (!Files.exists(filePath))
                continue;

            try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {

                ByteBuffer sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

                // 頂点バッファ
                sizeBuf.clear();
                channel.read(sizeBuf);
                sizeBuf.flip();
                int vbSize = sizeBuf.getInt();

                ByteBuffer vb = ByteBuffer.allocateDirect(vbSize);
                channel.read(vb);
                vb.flip();

                // インデックスバッファ
                sizeBuf.clear();
                channel.read(sizeBuf);
                sizeBuf.flip();
                int ibSize = sizeBuf.getInt();

                ByteBuffer ib = ByteBuffer.allocateDirect(ibSize);
                channel.read(ib);
                ib.flip();

                VertexFormat format = DefaultVertexFormat.BLOCK;
                VertexFormat.Mode mode = VertexFormat.Mode.QUADS;
                IndexType indexType = IndexType.SHORT;

                int vertexCount = vbSize / format.getVertexSize();
                int indexCount = ibSize / indexType.bytes;

                if (indexCount == 0) {
                    System.out.println("[BlockCache] Skipped cached mesh with empty index buffer: " + filePath);
                    continue;
                }

                RenderType rt = toRenderType(layer);
                if (rt != null) {
                    CachedMesh mesh = new CachedMesh(
                            vb, ib, vertexCount, indexCount,
                            format, mode, indexType);
                    result.put(rt, mesh);
                }

            } catch (Exception e) {
                System.out.println("[BlockCache] Failed to load cache: " + filePath);
                e.printStackTrace();
            }
        }

        return result;
    }

    // =====================================================
    // 単一ファイル読み込み
    // =====================================================
    public static void loadSingleFile(Path file) {
        try {
            String name = file.getFileName().toString();
            String[] parts = name.split("_");

            if (parts.length != 4)
                return;

            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            String layer = parts[3].replace(".bin", "");

            RenderType rt = toRenderType(layer);
            if (rt == null)
                return;

            long key = ChunkPos.asLong(x, z);

            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {

                ByteBuffer sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

                sizeBuf.clear();
                channel.read(sizeBuf);
                sizeBuf.flip();
                int vbSize = sizeBuf.getInt();

                ByteBuffer vb = ByteBuffer.allocateDirect(vbSize);
                channel.read(vb);
                vb.flip();

                sizeBuf.clear();
                channel.read(sizeBuf);
                sizeBuf.flip();
                int ibSize = sizeBuf.getInt();

                ByteBuffer ib = ByteBuffer.allocateDirect(ibSize);
                channel.read(ib);
                ib.flip();

                VertexFormat format = DefaultVertexFormat.BLOCK;
                VertexFormat.Mode mode = VertexFormat.Mode.QUADS;
                IndexType indexType = IndexType.SHORT;

                int vertexCount = vbSize / format.getVertexSize();
                int indexCount = ibSize / indexType.bytes;

                if (indexCount == 0) {
                    System.out.println("[BlockCache] Skipped cached mesh with empty index buffer (single): " + file);
                    return;
                }

                CachedMesh mesh = new CachedMesh(
                        vb, ib, vertexCount, indexCount,
                        format, mode, indexType);

                CACHE.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(rt, mesh);

            }

        } catch (Exception e) {
            System.out.println("[BlockCache] Failed to load " + file);
            e.printStackTrace();
        }
    }

    // =====================================================
    //  チャンク保存処理 
    // =====================================================
    public static void saveChunk(ChunkPos pos) {
        Path cacheDir = FMLPaths.GAMEDIR.get().resolve("blockcache/cache");

        System.out
                .println("[BlockCache] saveChunk: attempting to save chunk " + pos.x + "," + pos.z + " to " + cacheDir);
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            System.out.println("[BlockCache] Failed to create cache dir: " + cacheDir);
            e.printStackTrace();
            return;
        }

        long key = ChunkPos.asLong(pos.x, pos.z);
        Map<RenderType, CachedMesh> map = CACHE.get(key);
        if (map == null || map.isEmpty()) {
            System.out.println("[BlockCache] saveChunk: no cached mesh for " + pos.x + "," + pos.z);
            return;
        }

        for (var entry : map.entrySet()) {
            RenderType layer = entry.getKey();
            CachedMesh mesh = entry.getValue();

            String layerName = null;

            if (layer == RenderType.solid()) {
                layerName = "solid";
            } else if (layer == RenderType.cutout()) {
                layerName = "cutout";
            } else if (layer == RenderType.cutoutMipped()) {
                layerName = "cutout_mipped";
            } else if (layer == RenderType.translucent()) {
                layerName = "translucent";
            }

            if (layerName == null)
                continue;

            Path file = cacheDir.resolve(
                    "chunk_%d_%d_%s.bin".formatted(pos.x, pos.z, layerName));

            try (FileChannel ch = FileChannel.open(
                    file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {

                ByteBuffer size = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

                // --- VB ---
                ByteBuffer vb = mesh.vertexBuffer.duplicate();
                vb.clear();
                size.clear();
                size.putInt(vb.remaining()).flip();
                ch.write(size);
                ch.write(vb);

                // --- IB ---
                ByteBuffer ib = mesh.indexBuffer.duplicate();
                ib.clear();
                size.clear();
                size.putInt(ib.remaining()).flip();
                ch.write(size);
                ch.write(ib);

                System.out.println("[BlockCache] Saved cache: " + file);

            } catch (Exception e) {
                System.out.println("[BlockCache] Failed to save cache: " + file);
                e.printStackTrace();
            }
        }
    }

    // =====================================================
    //  全チャンク保存（追加）
    // =====================================================
    public static void saveAll() {
        for (long key : CACHE.keySet()) {
            int x = (int) (key >> 32);
            int z = (int) (key & 0xFFFFFFFFL);
            saveChunk(new ChunkPos(x, z));
        }
    }
}
