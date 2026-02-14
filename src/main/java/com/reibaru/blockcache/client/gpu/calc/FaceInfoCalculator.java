package com.reibaru.blockcache.client.gpu.calc;

import com.reibaru.blockcache.client.gpu.buffer.BlockData;
import com.reibaru.blockcache.client.gpu.tile.TileManager;

public class FaceInfoCalculator {

    public static int computeFaceInfo(TileManager manager,
                                      int tileX, int tileY, int tileZ,
                                      int x, int y, int z,
                                      BlockData self) {

        int faceInfo = 0;

        boolean selfTransparent = (self.flags & 1) == 0; // bit0 = solid

        // 透明系は6方向全部可視 + transparentフラグ
        if (selfTransparent) {
            faceInfo |= 0b0011_1111; // bit0〜bit5
            faceInfo |= (1 << 6);    // bit6 = transparent
            return faceInfo;
        }

        // solid → 隣が solid でない面だけ可視
        if (!isNeighborSolid(manager, tileX, tileY, tileZ, x + 1, y, z)) faceInfo |= (1 << 0); // +X
        if (!isNeighborSolid(manager, tileX, tileY, tileZ, x - 1, y, z)) faceInfo |= (1 << 1); // -X
        if (!isNeighborSolid(manager, tileX, tileY, tileZ, x, y + 1, z)) faceInfo |= (1 << 2); // +Y
        if (!isNeighborSolid(manager, tileX, tileY, tileZ, x, y - 1, z)) faceInfo |= (1 << 3); // -Y
        if (!isNeighborSolid(manager, tileX, tileY, tileZ, x, y, z + 1)) faceInfo |= (1 << 4); // +Z
        if (!isNeighborSolid(manager, tileX, tileY, tileZ, x, y, z - 1)) faceInfo |= (1 << 5); // -Z

        return faceInfo;
    }

    private static boolean isNeighborSolid(TileManager manager,
                                           int tileX, int tileY, int tileZ,
                                           int nx, int ny, int nz) {

        if (nx < 0) { tileX -= 1; nx = 15; }
        else if (nx > 15) { tileX += 1; nx = 0; }

        if (ny < 0) { tileY -= 1; ny = 15; }
        else if (ny > 15) { tileY += 1; ny = 0; }

        if (nz < 0) { tileZ -= 1; nz = 15; }
        else if (nz > 15) { tileZ += 1; nz = 0; }

        BlockData neighbor = manager.getBlockDataAt(tileX, tileY, tileZ, nx, ny, nz);
        if (neighbor == null) return false;

        return (neighbor.flags & 1) != 0; // bit0 = solid
    }
}
