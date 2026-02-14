package com.reibaru.blockcache.client.gpu.tile;

import com.reibaru.blockcache.client.gpu.buffer.TileBuffer;
import com.reibaru.blockcache.client.gpu.buffer.BlockData;

import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.BufferBuilder;

import java.util.*;

import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;

public class TileManager {

    public Collection<TileBuffer> getAllTiles() {
        return tiles.values();
    }

    // ★ 空気タイル判定マップ（tileX/tileY/tileZ ベース）
    private final ChunkTileAirMap airMap = new ChunkTileAirMap();

    public static TileManager INSTANCE;

    private final Map<Long, TileBuffer> tiles = new HashMap<>();

    // ★ 新規タイル（初期構築用）
    private final ArrayDeque<TileBuffer> pendingNewTiles = new ArrayDeque<>();

    // ★ dirty タイル（局所更新用）
    private final Set<Long> dirtyTiles = new HashSet<>();

    private final Level level;

    // ★ GC 削減用：スレッドごとの再利用 BlockPos
    private final ThreadLocal<BlockPos.MutableBlockPos> threadLocalPos =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    public TileManager(Level level) {
        this.level = level;
        INSTANCE = this;
    }

    // 座標パック
    private static long pack(int x, int y, int z) {
        return (((long)x & 0x1FFFFFL) << 42)
             | (((long)y & 0x1FFFFFL) << 21)
             | ((long)z & 0x1FFFFFL);
    }

    // ───────────────────────────────────────────────
    // ★ タイル取得 or 生成（新規タイルは「キューに積むだけ」）
    // ───────────────────────────────────────────────
    public TileBuffer getOrCreateTile(int tileX, int tileY, int tileZ) {

        if (airMap.isAir(tileX, tileY, tileZ)) { return null; }

        long key = pack(tileX, tileY, tileZ);

        return tiles.computeIfAbsent(key, k -> {
            TileBuffer tile = new TileBuffer(tileX, tileY, tileZ);
            pendingNewTiles.add(tile);
            return tile;
        });
    }

    public TileBuffer getTile(int tileX, int tileY, int tileZ) {
        return tiles.get(pack(tileX, tileY, tileZ));
    }

    public void markDirtyFromBlock(BlockPos pos) {
    int tileX = pos.getX() >> 4;
    int tileY = pos.getY() >> 4;
    int tileZ = pos.getZ() >> 4;

    markTileDirty(tileX, tileY, tileZ);
}



    // ───────────────────────────────────────────────
    // ★ dirty タイル登録（ブロック更新時）
    // ───────────────────────────────────────────────
    public void markTileDirty(int tileX, int tileY, int tileZ) {
        dirtyTiles.add(pack(tileX, tileY, tileZ));
    }

    // ───────────────────────────────────────────────
    // ★ 毎フレーム呼ぶ：タイル更新（同期のみ）
    // ───────────────────────────────────────────────
    public void tick() {

        // ======================================
        //  1) 空気タイルの自動削除
        // ======================================
        Iterator<Map.Entry<Long, TileBuffer>> it2 = tiles.entrySet().iterator();

        while (it2.hasNext()) {
            Map.Entry<Long, TileBuffer> e = it2.next();
            long key = e.getKey();
            TileBuffer tile = e.getValue();

            int tx = tile.getTileX();
            int ty = tile.getTileY();
            int tz = tile.getTileZ();

            // AirMap が「空気」と判定しているなら削除
            if (airMap.isAir(tx, ty, tz)) {

                // GPU メモリ解放
                GL43.glDeleteBuffers(tile.getSsboId());

                // TileManager から削除
                it2.remove();

                // dirtyTiles に残っていたら削除
                dirtyTiles.remove(key);

                //  隣接 dirty タイルも安全のため削除
                dirtyTiles.remove(pack(tx + 1, ty, tz));
                dirtyTiles.remove(pack(tx - 1, ty, tz));
                dirtyTiles.remove(pack(tx, ty + 1, tz));
                dirtyTiles.remove(pack(tx, ty - 1, tz));
                dirtyTiles.remove(pack(tx, ty, tz + 1));
                dirtyTiles.remove(pack(tx, ty, tz - 1));

                // ログ（任意）
                System.out.printf("[TileManager] removed empty tile (%d,%d,%d)%n", tx, ty, tz);
            }
        }

        // 2) 新規タイルの初期構築（フル faceInfo）
        int newBudget = 2;
        while (newBudget-- > 0 && !pendingNewTiles.isEmpty()) {
            TileBuffer tile = pendingNewTiles.poll();
            if (tile == null) continue;

            tile.loadFromWorld(level);
            tile.recalculateFaceInfoFull(this);
            tile.upload();
        }

        // 3) dirty タイルの局所更新（faceInfoLocal）
        int localBudget = 8; // ★ 1フレームあたり最大8タイルだけ更新
        Iterator<Long> it = dirtyTiles.iterator();

        while (localBudget-- > 0 && it.hasNext()) {
            long key = it.next();
            it.remove();

            TileBuffer tile = tiles.get(key);
            if (tile == null) continue;

            tile.recalculateFaceInfoLocal(this);

            if (tile.needsUpload()) {
                tile.upload();
            }
            updateAirMapForTile(tile);
        }
    }


    // ───────────────────────────────────────────────
    // ★ ブロック更新イベント
    // ───────────────────────────────────────────────
    public void onBlockChanged(BlockPos pos, BlockState newState) {

        int bx = pos.getX();
        int by = pos.getY();
        int bz = pos.getZ();

        int tileX = bx >> 4;
        int tileY = by >> 4;
        int tileZ = bz >> 4;

        System.out.println("[onBlockChanged] tile = (" + tileX + "," + tileY + "," + tileZ + ")");

        int localX = bx & 15;
        int localY = by & 15;
        int localZ = bz & 15;

        TileBuffer tile = getOrCreateTile(tileX, tileY, tileZ);

        BlockData data = tile.getBlockData(localX, localY, localZ);
        data.updateFromBlockState(newState, level, pos);

        tile.markBlockDirty(localX, localY, localZ);
        markTileDirty(tileX, tileY, tileZ);
    }

    // ───────────────────────────────────────────────
    // ★ BlockState 取得（faceInfo 計算用）
    // ───────────────────────────────────────────────
    public BlockState getBlockStateAt(int tileX, int tileY, int tileZ, int x, int y, int z) {
        int bx = (tileX << 4) + x;
        int by = (tileY << 4) + y;
        int bz = (tileZ << 4) + z;

        BlockPos.MutableBlockPos pos = threadLocalPos.get();
        pos.set(bx, by, bz);

        return level.getBlockState(pos);
    }

    public BlockData getBlockDataAt(int tileX, int tileY, int tileZ, int x, int y, int z) {
        TileBuffer tile = getTile(tileX, tileY, tileZ);
        if (tile == null) return null;
        return tile.getBlockData(x, y, z);
    }


        // ───────────────────────────────────────────────
        // ★ メッシュキャッシュから AirMap を更新
        // ───────────────────────────────────────────────
        public void updateAirMapFromMesh(ChunkPos pos,
                                 Map<RenderType, BufferBuilder.RenderedBuffer> layers) {

        // 必要ならここで airMap を一旦クリアしてもいい
        // airMap.clearForChunk(pos); みたいな感じで

        for (var entry : layers.entrySet()) {
           BufferBuilder.RenderedBuffer buf = entry.getValue();

           ByteBuffer vertexBuf = buf.vertexBuffer();
           ByteBuffer indexBuf  = buf.indexBuffer();

           if (vertexBuf == null || indexBuf == null) continue;
           if (!indexBuf.hasRemaining()) continue;

           vertexBuf.rewind();
           indexBuf.rewind();

           final int stride = 32; // 1頂点あたりのバイト数（バニラ固定想定）

           while (indexBuf.remaining() >= 6) {
             int i0 = indexBuf.getShort() & 0xFFFF;
             int i1 = indexBuf.getShort() & 0xFFFF;
             int i2 = indexBuf.getShort() & 0xFFFF;

             Vec3 p0 = readVertexPos(vertexBuf, i0, stride);
             Vec3 p1 = readVertexPos(vertexBuf, i1, stride);
             Vec3 p2 = readVertexPos(vertexBuf, i2, stride);

             double cx = (p0.x + p1.x + p2.x) / 3.0;
             double cy = (p0.y + p1.y + p2.y) / 3.0;
             double cz = (p0.z + p1.z + p2.z) / 3.0;

             int tileX = ((int)Math.floor(cx)) >> 4;
             int tileY = ((int)Math.floor(cy)) >> 4;
             int tileZ = ((int)Math.floor(cz)) >> 4;

             airMap.markNonAir(tileX, tileY, tileZ);
           }
        }
    }

    private Vec3 readVertexPos(ByteBuffer buf, int index, int stride) {
       int base = index * stride;

       float x = buf.getFloat(base);
       float y = buf.getFloat(base + 4);
       float z = buf.getFloat(base + 8);

       return new Vec3(x, y, z);
    }

    public void updateAirMapForTile(TileBuffer tile) {
        int tileX = tile.getTileX();
        int tileY = tile.getTileY();
        int tileZ = tile.getTileZ();

        boolean hasSolid = false;

        // タイル内のブロックを走査して「1つでも非空気があるか」を調べる
        for (int x = 0; x < 16 && !hasSolid; x++) {
            for (int y = 0; y < 16 && !hasSolid; y++) {
                for (int z = 0; z < 16 && !hasSolid; z++) {
                    BlockData data = tile.getBlockData(x, y, z);
                    if (!data.isAir()) {
                        hasSolid = true;
                    }
                }
            }
        }

        if (hasSolid) {
            airMap.markNonAir(tileX, tileY, tileZ);
        } else {
            airMap.markAir(tileX, tileY, tileZ);
        }
    }

    }
                                 


