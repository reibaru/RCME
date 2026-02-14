package com.reibaru.blockcache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

public class BlockCacheState {

    private static double lastX = Double.NaN;
    private static double lastY = Double.NaN;
    private static double lastZ = Double.NaN;

    private static double lastRotX = Double.NaN;
    private static double lastRotY = Double.NaN;

    private static final double POSITION_THRESHOLD = 0.1;
    private static final double ANGLE_THRESHOLD_DEGREES = 5.0;

    public static boolean positionChangedEnough() {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (cam == null) return true;

        Vec3 pos = cam.getPosition();
        if (Double.isNaN(lastX)) return true;

        double dx = pos.x - lastX;
        double dy = pos.y - lastY;
        double dz = pos.z - lastZ;

        return Math.sqrt(dx*dx + dy*dy + dz*dz) > POSITION_THRESHOLD;
    }

    public static boolean angleChangedEnough() {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (cam == null) return true;

        double rotX = cam.getXRot();
        double rotY = cam.getYRot();

        if (Double.isNaN(lastRotX)) return true;

        return Math.abs(rotX - lastRotX) >= ANGLE_THRESHOLD_DEGREES
            || Math.abs(rotY - lastRotY) >= ANGLE_THRESHOLD_DEGREES;
    }

    public static void update() {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (cam == null) return;

        Vec3 pos = cam.getPosition();
        lastX = pos.x;
        lastY = pos.y;
        lastZ = pos.z;

        lastRotX = cam.getXRot();
        lastRotY = cam.getYRot();
    }

    public static void reset() {
        lastX = lastY = lastZ = Double.NaN;
        lastRotX = lastRotY = Double.NaN;
    }
}
