package com.reibaru.blockcache.client.gpu.tile;

import java.util.HashSet;
import java.util.Set;

public class ChunkTileAirMap {

    // ★ 空気タイルのキーを保持（1bit/タイルでもいいが、まずはシンプルに）
    private final Set<Long> airTiles = new HashSet<>();

    // ★ 空気タイルとして登録
    public void markAir(int tileX, int tileY, int tileZ) {
        airTiles.add(pack(tileX, tileY, tileZ));
    }

    // ★ 空気タイルかどうか
    public boolean isAir(int tileX, int tileY, int tileZ) {
        return airTiles.contains(pack(tileX, tileY, tileZ));
    }

    // ★ TileManager と同じ pack を使う（共通化してもOK）
    private static long pack(int x, int y, int z) {
        return (((long)x & 0x1FFFFFL) << 42)
             | (((long)y & 0x1FFFFFL) << 21)
             | ((long)z & 0x1FFFFFL);
    }

    public void markNonAir(int tileX, int tileY, int tileZ) {
    long key = pack(tileX, tileY, tileZ);
    airTiles.add(key);
}

}
