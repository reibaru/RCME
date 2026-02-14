package com.reibaru.blockcache.client.gpu.buffer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.core.Direction;

public class BlockData {

    public int modelId;
    public int meta;
    public int light;
    public int flags;
    public int faceInfo;
    public int color; // ★ ARGB

    public boolean isAir() {
        return modelId == 0;
    }

    public void updateFromBlockState(BlockState state, Level level, BlockPos pos) {

        if (state.isAir()) {
            this.modelId = 0;
            this.meta = 0;
            this.light = 0;
            this.flags = 0;
            this.faceInfo = 0;
            this.color = 0xFF000000;
            return;
        }

        this.modelId = BuiltInRegistries.BLOCK.getId(state.getBlock());

        int m = 0;
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction dir = state.getValue(BlockStateProperties.FACING);
            m |= (dir.ordinal() & 0b111);
        } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            m |= (dir.ordinal() & 0b111);
        }
        if (state.hasProperty(BlockStateProperties.HALF)) {
            Half h = state.getValue(BlockStateProperties.HALF);
            int halfBit = (h == Half.TOP) ? 1 : 0;
            m |= (halfBit << 3);
        }
        this.meta = m & 0xFF;

        int sky = level.getBrightness(LightLayer.SKY, pos) & 0xF;
        int block = level.getBrightness(LightLayer.BLOCK, pos) & 0xF;
        this.light = (sky << 4) | block;

        int f = 0;
        if (state.isSolid() && state.getRenderShape() != RenderShape.INVISIBLE) {
            f |= 1;
        }
        if (state.getBlock() instanceof LeavesBlock) {
            f |= 1 << 1;
        }
        if (state.getBlock() instanceof GlassBlock) {
            f |= 1 << 2;
        }
        if (!state.getFluidState().isEmpty()) {
            f |= 1 << 3;
        }
        if ((level.getBrightness(LightLayer.BLOCK, pos) & 0xF) > 0) {
            f |= 1 << 4;
        }
        this.flags = f;

        // ★ 色（とりあえず MapColor ベース）
        int rgb = state.getMapColor(level, pos).col; // 0xRRGGBB
        this.color = 0xFF000000 | rgb;               // ARGB
    }

    public long packToLong() {
        if (modelId == 0) return 0L;

        long v = 0L;
        // 下位32bit：ARGB
        v |= (long)(color & 0xFFFFFFFFL);          // bit 0-31
        // 上位32bit：属性
        v |= (long)(light    & 0xFFL) << 32;       // bit 32-39
        v |= (long)(flags    & 0xFFL) << 40;       // bit 40-47
        v |= (long)(faceInfo & 0xFFL) << 48;       // bit 48-55
        v |= (long)(meta     & 0xFFL) << 56;       // bit 56-63
        return v;
    }
}

