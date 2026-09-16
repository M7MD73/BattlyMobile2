package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FramePacingPolicyTest {
    @Test
    public void usesTheCurrentDisplayModeInsteadOfInventing120Hz() {
        assertEquals(60f, FramePacingPolicy.selectSurfaceFrameRate(60f,
                new float[0]), 0.01f);
        assertEquals(90f, FramePacingPolicy.selectSurfaceFrameRate(90f,
                new float[]{60f, 120f}), 0.01f);
    }

    @Test
    public void fallsBackToARealAlternativeWhenCurrentModeIsInvalid() {
        assertEquals(120f, FramePacingPolicy.selectSurfaceFrameRate(0f,
                new float[]{60f, Float.NaN, 120f}), 0.01f);
    }

    @Test
    public void letsAndroidChooseWhenNoValidModeIsKnown() {
        assertEquals(0f, FramePacingPolicy.selectSurfaceFrameRate(Float.NaN,
                new float[]{0f, -1f}), 0.01f);
    }

    @Test
    public void alternateSurfaceSelectionIsHonored() {
        assertTrue(FramePacingPolicy.useSurfaceView(true));
        assertFalse(FramePacingPolicy.useSurfaceView(false));
    }

    @Test
    public void legacyOnePlusSixUsesTextureViewToAvoidRotatedOutput() {
        assertFalse(FramePacingPolicy.useSurfaceView(
                true, 30, "OnePlus", "ONEPLUS A6013", "opengles_mobileglues"));
        assertFalse(FramePacingPolicy.useSurfaceView(
                true, 29, "OnePlus", "ONEPLUS A6003", "opengles_mobileglues"));
    }

    @Test
    public void surfaceViewRemainsAvailableOutsideAffectedFirmware() {
        assertTrue(FramePacingPolicy.useSurfaceView(
                true, 31, "OnePlus", "ONEPLUS A6013", "opengles_mobileglues"));
        assertTrue(FramePacingPolicy.useSurfaceView(
                true, 30, "OnePlus", "NE2213", "opengles_mobileglues"));
        assertTrue(FramePacingPolicy.useSurfaceView(
                true, 30, "Samsung", "SM-S942B", "opengles_mobileglues"));
        assertFalse(FramePacingPolicy.useSurfaceView(
                false, 30, "Samsung", "SM-S942B", "opengles_mobileglues"));
    }

    @Test
    public void vulkanBackendsUseTextureViewToAvoidSwapchainPreRotation() {
        assertFalse(FramePacingPolicy.useSurfaceView(
                true, 35, "Samsung", "SM-S938B", "opengles3_desktopgl_zink_kopper"));
        assertFalse(FramePacingPolicy.useSurfaceView(
                true, 35, "Xiaomi", "2406APNFAG", "opengles3_desktopgl_freedreno"));
        assertFalse(FramePacingPolicy.useSurfaceView(
                true, 35, "Google", "Pixel 10", "vulkan_zink"));
    }
}
