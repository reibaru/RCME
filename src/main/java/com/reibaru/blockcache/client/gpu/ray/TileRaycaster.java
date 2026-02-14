package com.reibaru.blockcache.client.gpu.ray;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.reibaru.blockcache.client.gpu.pixel.TilePixel;
import com.reibaru.blockcache.client.gpu.texture.TextureAtlasIndexManager;

public class TileRaycaster {

    public static final TileRaycaster INSTANCE = new TileRaycaster();

    private final Minecraft mc = Minecraft.getInstance();
    private ScreenTileBuffer screenTileBuffer;

    private TextureAtlasIndexManager atlasIndexManager;

    public void setAtlasIndexManager(TextureAtlasIndexManager manager) {
        this.atlasIndexManager = manager;
    }

    public ScreenTileBuffer getScreenTileBuffer() {
        return screenTileBuffer;
    }

    public void update(ScreenTileBuffer buffer) {
        this.screenTileBuffer = buffer;

        if (mc.level == null || mc.player == null || atlasIndexManager == null) {
            // まだ準備ができていない場合は全部空タイルにしておく
            for (int i = 0; i < buffer.getTileCount(); i++) {
                buffer.set(i, new TilePixel());
            }
            return;
        }

        int tilesX = buffer.getTilesX();
        int tilesY = buffer.getTilesY();
        int tiles = buffer.getTileCount();

        int sw = mc.getWindow().getWidth();
        int sh = mc.getWindow().getHeight();

        float tileW = (float) sw / tilesX;
        float tileH = (float) sh / tilesY;

        for (int i = 0; i < tiles; i++) {
            int tx = i % tilesX;
            int ty = i / tilesX;

            float centerX = (tx + 0.5f) * tileW;
            float centerY = (ty + 0.5f) * tileH;

            HitResult hit = raycastFromScreen(centerX, centerY);
            TilePixel px = sampleBlockTexture(hit);

            buffer.set(i, px);
        }
    }

    private HitResult raycastFromScreen(float x, float y) {
        if (mc.level == null)
            return null;

        var camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        int sw = mc.getWindow().getWidth();
        int sh = mc.getWindow().getHeight();

        float ndcX = (2.0f * x) / sw - 1.0f;
        float ndcY = 1.0f - (2.0f * y) / sh;

        float fov = mc.options.fov().get().floatValue();
        float fovRad = (float) Math.toRadians(fov);
        float tanHalfFov = (float) Math.tan(fovRad * 0.5f);

        float aspect = (float) sw / (float) sh;

        float cx = -ndcX * tanHalfFov * aspect;
        float cy = ndcY * tanHalfFov;
        float cz = -1.0f;

        Vector4f camDir = new Vector4f(cx, cy, cz, 0.0f);

        Matrix4f viewInv = new Matrix4f()
                .translate((float) camPos.x, (float) camPos.y, (float) camPos.z)
                .rotate(camera.rotation().invert(new Quaternionf()));

        camDir.mul(viewInv);

        Vec3 dir = new Vec3(camDir.x(), camDir.y(), camDir.z()).normalize();
        Vec3 end = camPos.add(dir.scale(200.0));

        return mc.level.clip(new ClipContext(
                camPos,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
    }

    private TilePixel sampleBlockTexture(HitResult hit) {
        TilePixel px = new TilePixel();

        if (hit == null || hit.getType() != HitResult.Type.BLOCK || mc.level == null || atlasIndexManager == null) {
            px.texIndex = -1f;
            px.u = 0f;
            px.v = 0f;
            px.depth = 9999f;
            return px;
        }

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        var face = bhr.getDirection();
        var model = mc.getBlockRenderer().getBlockModel(state);

        var random = RandomSource.create(0);
        var quads = model.getQuads(state, face, random);

        if (quads.isEmpty()) {
            px.texIndex = -1f;
            px.depth = computeDepth(hit);
            return px;
        }

        var quad = quads.get(0);
        var sprite = quad.getSprite();

        int[] data = quad.getVertices();

        // 4頂点の位置とUV
        Vector3f[] verts = new Vector3f[4];
        float[] uvsU = new float[4];
        float[] uvsV = new float[4];

        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            float x = Float.intBitsToFloat(data[base + 0]);
            float y = Float.intBitsToFloat(data[base + 1]);
            float z = Float.intBitsToFloat(data[base + 2]);
            float u = Float.intBitsToFloat(data[base + 4]);
            float v = Float.intBitsToFloat(data[base + 5]);

            verts[i] = new Vector3f(x, y, z);

            uvsU[i] = u;
            uvsV[i] = v;
        }

        // ヒット位置（ブロックローカル）
        Vec3 hitPos = hit.getLocation();
        float hx = (float) (hitPos.x - pos.getX());
        float hy = (float) (hitPos.y - pos.getY());
        float hz = (float) (hitPos.z - pos.getZ());

        float px2, py2;
        float v0x, v0y, v1x, v1y, v2x, v2y, v3x, v3y;

        switch (face) {
            case UP -> {
                // 上面 → (x, z)
                px2 = hx;
                py2 = hz;

                v0x = verts[0].x;
                v0y = verts[0].z;
                v1x = verts[1].x;
                v1y = verts[1].z;
                v2x = verts[2].x;
                v2y = verts[2].z;
                v3x = verts[3].x;
                v3y = verts[3].z;
            }
            case DOWN -> {
                // 下面は上下反転
                px2 = hx;
                py2 = 1.0f - hz;

                v0x = verts[0].x;
                v0y = 1.0f - verts[0].z;
                v1x = verts[1].x;
                v1y = 1.0f - verts[1].z;
                v2x = verts[2].x;
                v2y = 1.0f - verts[2].z;
                v3x = verts[3].x;
                v3y = 1.0f - verts[3].z;
            }
            case NORTH -> {
                // 北面は U 軸が反転
                px2 = 1.0f - hx;
                py2 = hy;

                v0x = 1.0f - verts[0].x;
                v0y = verts[0].y;
                v1x = 1.0f - verts[1].x;
                v1y = verts[1].y;
                v2x = 1.0f - verts[2].x;
                v2y = verts[2].y;
                v3x = 1.0f - verts[3].x;
                v3y = verts[3].y;
            }
            case SOUTH -> {
                // 南面は反転なし
                px2 = hx;
                py2 = hy;

                v0x = verts[0].x;
                v0y = verts[0].y;
                v1x = verts[1].x;
                v1y = verts[1].y;
                v2x = verts[2].x;
                v2y = verts[2].y;
                v3x = verts[3].x;
                v3y = verts[3].y;
            }
            case EAST -> {
                // 東面は U 軸反転
                px2 = 1.0f - hz;
                py2 = hy;

                v0x = 1.0f - verts[0].z;
                v0y = verts[0].y;
                v1x = 1.0f - verts[1].z;
                v1y = verts[1].y;
                v2x = 1.0f - verts[2].z;
                v2y = verts[2].y;
                v3x = 1.0f - verts[3].z;
                v3y = verts[3].y;
            }
            case WEST -> {
                // 西面は反転なし
                px2 = hz;
                py2 = hy;

                v0x = verts[0].z;
                v0y = verts[0].y;
                v1x = verts[1].z;
                v1y = verts[1].y;
                v2x = verts[2].z;
                v2y = verts[2].y;
                v3x = verts[3].z;
                v3y = verts[3].y;
            }
            default -> {
                px.texIndex = -1f;
                px.depth = computeDepth(hit);
                return px;
            }
        }

        // --- 三角形0-1-2 ---
        float denomA = (v1y - v2y) * (v0x - v2x) + (v2x - v1x) * (v0y - v2y);
        if (denomA == 0.0f) {
            px.texIndex = -1f;
            px.depth = computeDepth(hit);
            return px;
        }

        float w1A = ((v1y - v2y) * (px2 - v2x) + (v2x - v1x) * (py2 - v2y)) / denomA;
        float w2A = ((v2y - v0y) * (px2 - v2x) + (v0x - v2x) * (py2 - v2y)) / denomA;
        float w3A = 1.0f - w1A - w2A;

        boolean insideA = (w1A >= 0 && w2A >= 0 && w3A >= 0);

        // --- 三角形0-2-3 ---
        float denomB = (v2y - v3y) * (v0x - v3x) + (v3x - v2x) * (v0y - v3y);
        if (denomB == 0.0f) {
            px.texIndex = -1f;
            px.depth = computeDepth(hit);
            return px;
        }

        float w1B = ((v2y - v3y) * (px2 - v3x) + (v3x - v2x) * (py2 - v3y)) / denomB;
        float w2B = ((v3y - v0y) * (px2 - v3x) + (v0x - v3x) * (py2 - v3y)) / denomB;
        float w3B = 1.0f - w1B - w2B;

        boolean insideB = (w1B >= 0 && w2B >= 0 && w3B >= 0);

        float u, v;
        if (insideA) {
            u = uvsU[0] * w1A + uvsU[1] * w2A + uvsU[2] * w3A;
            v = uvsV[0] * w1A + uvsV[1] * w2A + uvsV[2] * w3A;
        } else if (insideB) {
            // 三角形 2-3-0 の UV 補間
            u = uvsU[2] * w1B + uvsU[3] * w2B + uvsU[0] * w3B;
            v = uvsV[2] * w1B + uvsV[3] * w2B + uvsV[0] * w3B;
        } else {
            px.texIndex = -1f;
            px.depth = computeDepth(hit);
            return px;
        }

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        float localU = (u - u0) / (u1 - u0);
        float localV = (v - v0) / (v1 - v0);

        px.u = localU;
        px.v = localV;
        px.texIndex = atlasIndexManager.getIndex(sprite);
        px.depth = computeDepth(hit);
        return px;
    }

    private float computeDepth(HitResult hit) {
        if (hit == null)
            return 9999f;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        return (float) cam.distanceTo(hit.getLocation());
    }
}
