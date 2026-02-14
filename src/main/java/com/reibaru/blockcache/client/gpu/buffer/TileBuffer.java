package com.reibaru.blockcache.client.gpu.buffer;

import com.reibaru.blockcache.client.gpu.calc.FaceInfoCalculator;
import com.reibaru.blockcache.client.gpu.tile.TileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL43;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

public class TileBuffer {

    private static PrintWriter debugLog;

    static {
        try {
            debugLog = new PrintWriter(new FileWriter("tile_debug.log", true), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private final int tileX, tileY, tileZ;
    private final BlockData[] blocks = new BlockData[16 * 16 * 16];

    private final byte[] prevFaceInfo = new byte[16 * 16 * 16];

    private final List<Integer> dirtyBlocks = new ArrayList<>();
    private final boolean[] dirtyMask = new boolean[16 * 16 * 16];

    private final ByteBuffer uploadBuffer =
            ByteBuffer.allocateDirect(16 * 16 * 16 * Long.BYTES)
                    .order(ByteOrder.nativeOrder());

    private boolean needsUpload = true;

    private final int ssboId;

    public TileBuffer(int tileX, int tileY, int tileZ) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.tileZ = tileZ;

        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = new BlockData();
        }

        ssboId = GL43.glGenBuffers();
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
        GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                (long) blocks.length * Long.BYTES,
                GL43.GL_DYNAMIC_DRAW);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        // ★ タイル生成ログ（生存確認用）
        debugLog.printf("[create] TileBuffer (%d,%d,%d)%n", tileX, tileY, tileZ);
    }

    private static int index(int x, int y, int z) {
        return x + (z * 16) + (y * 256);
    }

    public BlockData getBlockData(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    public void loadFromWorld(Level level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {

                    int wx = (tileX << 4) + x;
                    int wy = (tileY << 4) + y;
                    int wz = (tileZ << 4) + z;

                    pos.set(wx, wy, wz);
                    BlockState state = level.getBlockState(pos);

                    BlockData data = getBlockData(x, y, z);
                    data.updateFromBlockState(state, level, pos);
                }
            }
        }
    }

    public void recalculateFaceInfoFull(TileManager manager) {
        boolean changed = false;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int idx = index(x, y, z);

                    BlockData self = getBlockData(x, y, z);

                    byte oldFace = prevFaceInfo[idx];

                    int newFace = FaceInfoCalculator.computeFaceInfo(
                            manager, tileX, tileY, tileZ, x, y, z, self
                    );

                    self.faceInfo = newFace;

                    byte newFaceByte = (byte) (newFace & 0xFF);
                    if (newFaceByte != oldFace) {
                        changed = true;
                    }

                    prevFaceInfo[idx] = newFaceByte;
                }
            }
        }

        this.needsUpload = changed;
    }

    public void markBlockDirty(int x, int y, int z) {
        int idx = index(x, y, z);
        if (!dirtyMask[idx]) {
            dirtyMask[idx] = true;
            dirtyBlocks.add(idx);
        }
    }

    public void recalculateFaceInfoLocal(TileManager manager) {
        if (dirtyBlocks.isEmpty()) return;

        long start = System.nanoTime();

        for (int packed : dirtyBlocks) {
            int x = packed & 15;
            int z = (packed >> 4) & 15;
            int y = (packed >> 8) & 15;

            updateFaceInfoAt(manager, x, y, z);
            updateFaceInfoAt(manager, x + 1, y, z);
            updateFaceInfoAt(manager, x - 1, y, z);
            updateFaceInfoAt(manager, x, y + 1, z);
            updateFaceInfoAt(manager, x, y - 1, z);
            updateFaceInfoAt(manager, x, y, z + 1);
            updateFaceInfoAt(manager, x, y, z - 1);
        }

        for (int idx : dirtyBlocks) {
            dirtyMask[idx] = false;
        }
        dirtyBlocks.clear();

        long end = System.nanoTime();

        // ★ ファイルログ
        debugLog.printf("[faceInfoLocal] tile (%d,%d,%d) took %.2f ms%n",
                tileX, tileY, tileZ, (end - start) / 1_000_000.0);
    }

    private void updateFaceInfoAt(TileManager manager, int x, int y, int z) {
        if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
            return;
        }

        int idx = index(x, y, z);

        BlockData self = blocks[idx];
        if (self == null) return;

        int newFace = FaceInfoCalculator.computeFaceInfo(
                manager, tileX, tileY, tileZ, x, y, z, self
        );

        byte newFaceByte = (byte) (newFace & 0xFF);
        if (newFaceByte != prevFaceInfo[idx]) {
            prevFaceInfo[idx] = newFaceByte;
            self.faceInfo = newFace;
            needsUpload = true;
        }
    }

    public void upload() {
        if (!needsUpload) return;

        long start = System.nanoTime();

        uploadBuffer.clear();
        LongBuffer lb = uploadBuffer.asLongBuffer();

        for (int i = 0; i < blocks.length; i++) {
            lb.put(blocks[i].packToLong());
        }
        lb.flip();

        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
        GL43.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, uploadBuffer);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        needsUpload = false;

        long end = System.nanoTime();

        // ★ ファイルログ
        debugLog.printf("[upload] tile (%d,%d,%d) took %.2f ms%n",
                tileX, tileY, tileZ, (end - start) / 1_000_000.0);
    }

    public boolean needsUpload() {
        return needsUpload;
    }

    public int getSsboId() {
        return ssboId;
    }

    public int worldX() { return tileX << 4; }
    public int worldY() { return tileY << 4; }
    public int worldZ() { return tileZ << 4; }

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public int getTileZ() {
        return tileZ;
    }

}
