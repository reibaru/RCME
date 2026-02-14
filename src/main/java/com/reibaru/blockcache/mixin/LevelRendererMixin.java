package com.reibaru.blockcache.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Queue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = LevelRenderer.class)
public class LevelRendererMixin {

    // --- Shadow ---

    @Shadow private double prevCamRotX;
    @Shadow private double prevCamRotY;

    @Shadow
    private void applyFrustum(Frustum frustum) {
        throw new AssertionError();
    }

    // --- private メソッド updateRenderChunks へのアクセサ ---
    // LevelRenderer#updateRenderChunks(
    //   LinkedHashSet<LevelRenderer.RenderChunkInfo>,
    //   LevelRenderer.RenderInfoMap,
    //   Vec3,
    //   Queue<LevelRenderer.RenderChunkInfo>,
    //   boolean
    // )
    @Invoker("updateRenderChunks")
    void blockcache$invokeUpdateRenderChunks(
        Object renderChunks,
        Object infoMap,
        Object cameraPos,
        Object queue,
        boolean smartCull
) {
    throw new AssertionError();
}



    // --- 自前で保持するカメラ参照 ---
    @Unique
    private Camera blockcache$currentCamera;

    // --- ワールド読み込み時刻（改善案A） ---
    @Unique private long blockcache$worldLoadTime = 0L;
    @Unique private static final long BLOCKCACHE_INITIAL_GRACE_PERIOD_MS = 5000L; // 5秒

    @Unique
    private boolean blockcache$isInInitialGracePeriod() {
        return System.currentTimeMillis() - blockcache$worldLoadTime < BLOCKCACHE_INITIAL_GRACE_PERIOD_MS;
    }

    // --- 角度しきい値 ---
    @Unique private double blockcache$lastAppliedRotX = Double.NaN;
    @Unique private double blockcache$lastAppliedRotY = Double.NaN;

    @Unique private static final double BLOCKCACHE_ANGLE_THRESHOLD_DEGREES = 5.0;

    // --- 位置しきい値 ---
    @Unique private double blockcache$lastX = Double.NaN;
    @Unique private double blockcache$lastY = Double.NaN;
    @Unique private double blockcache$lastZ = Double.NaN;

    @Unique private static final double BLOCKCACHE_POSITION_THRESHOLD = 0.1;

    // =====================================================
    // setupRender の先頭で Camera をキャプチャ
    // =====================================================
    @Inject(method = "setupRender", at = @At("HEAD"))
    private void blockcache$captureCamera(
            Camera camera,
            Frustum frustum,
            boolean p_194341_,
            boolean p_194342_,
            CallbackInfo ci
    ) {
        this.blockcache$currentCamera = camera;
    }

    // =====================================================
    // ワールド切り替え時の状態リセット
    // =====================================================
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void blockcache$onWorldChange(ClientLevel level, CallbackInfo ci) {
        blockcache$lastX = Double.NaN;
        blockcache$lastY = Double.NaN;
        blockcache$lastZ = Double.NaN;
        blockcache$lastAppliedRotX = Double.NaN;
        blockcache$lastAppliedRotY = Double.NaN;

        blockcache$worldLoadTime = System.currentTimeMillis();
    }

    // =====================================================
    // フラスタム適用の抑制（第1・2フェーズ）
    // =====================================================
    @Redirect(
            method = "setupRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" +
                             "applyFrustum(Lnet/minecraft/client/renderer/culling/Frustum;)V"
            )
    )
    private void blockcache$maybeApplyFrustum(LevelRenderer ignored, Frustum frustum) {

        if (Double.isNaN(blockcache$lastAppliedRotX)) {
            this.applyFrustum(frustum);
            blockcache$updateState();
            return;
        }

        if (blockcache$positionChangedEnough()) {
            this.applyFrustum(frustum);
            blockcache$updateState();
            return;
        }

        if (blockcache$angleChangedEnough()) {
            this.applyFrustum(frustum);
            blockcache$updateState();
            return;
        }

        // どれも変化していなければフラスタム更新をスキップ
    }

    // =====================================================
    // full update 抑制（第三フェーズ）
    // lambda$setupRender$2 内の updateRenderChunks 呼び出しをねらう
    // =====================================================
    @Inject(
            method = "lambda$setupRender$2",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" +
                             "updateRenderChunks(Ljava/util/LinkedHashSet;" +
                             "Lnet/minecraft/client/renderer/LevelRenderer$RenderInfoMap;" +
                             "Lnet/minecraft/world/phys/Vec3;" +
                             "Ljava/util/Queue;" +
                             "Z)V"
            ),
            cancellable = true
    )
    private void blockcache$maybeSkipFullUpdate(CallbackInfo ci) {

        if (Double.isNaN(blockcache$lastX)) {
            // 初回は通常どおり full update
            return;
        }

        if (!blockcache$positionChangedEnough()) {
            // 十分動いていなければ full update をスキップ
            ci.cancel();
        }
    }

    // =====================================================
    // partial update 抑制（第四フェーズ）
    // setupRender 内の partial update 用 updateRenderChunks を Redirect
    // =====================================================
    @Redirect(
            method = "setupRender",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/LevelRenderer;" +
                                     "updateRenderChunks(Ljava/util/LinkedHashSet;" +
                                     "Lnet/minecraft/client/renderer/LevelRenderer$RenderInfoMap;" +
                                     "Lnet/minecraft/world/phys/Vec3;" +
                                     "Ljava/util/Queue;" +
                                     "Z)V"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/LevelRenderer;" +
                                     "applyFrustum(Lnet/minecraft/client/renderer/culling/Frustum;)V"
                    )
            ),
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" +
                             "updateRenderChunks(Ljava/util/LinkedHashSet;" +
                             "Lnet/minecraft/client/renderer/LevelRenderer$RenderInfoMap;" +
                             "Lnet/minecraft/world/phys/Vec3;" +
                             "Ljava/util/Queue;" +
                             "Z)V"
            )
    )
    private void blockcache$maybeUpdateRenderChunksPartial(
            LevelRenderer instance,
            LinkedHashSet<?> renderChunks,
            Object infoMap,   // LevelRenderer.RenderInfoMap
            Vec3 cameraPos,
            Queue<?> queue,
            boolean smartCull
    ) {
        // 初期猶予期間中は普通に更新
        if (blockcache$isInInitialGracePeriod()) {
            blockcache$invokeUpdateRenderChunks(renderChunks, infoMap, cameraPos, queue, smartCull);
            blockcache$updateState();
            return;
        }

        // まだ基準位置が未設定なら普通に更新
        if (Double.isNaN(blockcache$lastX)) {
            blockcache$invokeUpdateRenderChunks(renderChunks, infoMap, cameraPos, queue, smartCull);
            blockcache$updateState();
            return;
        }

        // キューが空ならやることがない
        if (queue.isEmpty()) {
            return;
        }

        // 一定以上動いていれば partial update 実行
        if (blockcache$positionChangedEnough()) {
            blockcache$invokeUpdateRenderChunks(renderChunks, infoMap, cameraPos, queue, smartCull);
            blockcache$updateState();
        }
        // それ以外の場合は partial update をスキップ
        // （frustum 更新もスキップされる）
    }

    // =====================================================
    // 共通ヘルパー
    // =====================================================

    @Unique
    private boolean blockcache$positionChangedEnough() {
        Camera cam = this.blockcache$currentCamera;
        if (cam == null) return true;

        Vec3 pos = cam.getPosition();
        double dx = pos.x - blockcache$lastX;
        double dy = pos.y - blockcache$lastY;
        double dz = pos.z - blockcache$lastZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz) > BLOCKCACHE_POSITION_THRESHOLD;
    }

    @Unique
    private boolean blockcache$angleChangedEnough() {
        double dx = Math.abs(prevCamRotX - blockcache$lastAppliedRotX) * 2.0;
        double dy = Math.abs(prevCamRotY - blockcache$lastAppliedRotY) * 2.0;

        return dx >= BLOCKCACHE_ANGLE_THRESHOLD_DEGREES
                || dy >= BLOCKCACHE_ANGLE_THRESHOLD_DEGREES;
    }

    @Unique
    private void blockcache$updateState() {
        Camera cam = this.blockcache$currentCamera;
        if (cam != null) {
            Vec3 pos = cam.getPosition();
            blockcache$lastX = pos.x;
            blockcache$lastY = pos.y;
            blockcache$lastZ = pos.z;
        }

        blockcache$lastAppliedRotX = prevCamRotX;
        blockcache$lastAppliedRotY = prevCamRotY;
    }
}
