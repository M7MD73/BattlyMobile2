package net.kdt.pojavlaunch.utils;

import android.os.Build;

import androidx.annotation.Nullable;

import java.util.Locale;

/** Pure decisions shared by the Android surface and frame-pacing tests. */
public final class FramePacingPolicy {
    private FramePacingPolicy() {
    }

    public static float selectSurfaceFrameRate(float currentRefreshRate,
                                               @Nullable float[] alternativeRefreshRates) {
        return selectSurfaceFrameRate(currentRefreshRate, alternativeRefreshRates, null);
    }

    public static float selectSurfaceFrameRate(float currentRefreshRate,
                                               @Nullable float[] alternativeRefreshRates,
                                               @Nullable float[] supportedRefreshRates) {
        float bestSupported = highestValidRefreshRate(supportedRefreshRates);
        if (bestSupported > 0f) {
            return bestSupported;
        }
        if (isValidRefreshRate(currentRefreshRate)) {
            return currentRefreshRate;
        }

        return highestValidRefreshRate(alternativeRefreshRates);
    }

    public static boolean useSurfaceView(boolean alternateSurfaceEnabled) {
        return useSurfaceView(alternateSurfaceEnabled, Build.VERSION.SDK_INT,
                Build.MANUFACTURER, Build.MODEL, null);
    }

    public static boolean useSurfaceView(boolean alternateSurfaceEnabled,
                                         @Nullable String renderer) {
        return useSurfaceView(alternateSurfaceEnabled, Build.VERSION.SDK_INT,
                Build.MANUFACTURER, Build.MODEL, renderer);
    }

    static boolean useSurfaceView(boolean alternateSurfaceEnabled, int sdkInt,
                                  @Nullable String manufacturer, @Nullable String model,
                                  @Nullable String renderer) {
        if (!alternateSurfaceEnabled) return false;

        String normalizedRenderer = normalize(renderer);
        if (normalizedRenderer.contains("zink")
                || normalizedRenderer.contains("freedreno")
                || normalizedRenderer.startsWith("vulkan")) {
            return false;
        }

        // OxygenOS 10/11 reports a pre-rotated SurfaceView buffer for the OnePlus 6/6T.
        // SurfaceFlinger then applies the display transform again, rotating only Minecraft.
        String normalizedManufacturer = normalize(manufacturer);
        String normalizedModel = normalize(model);
        boolean legacyOnePlusSix = "oneplus".equals(normalizedManufacturer)
                && normalizedModel.startsWith("oneplus a60");
        return !legacyOnePlusSix || sdkInt >= Build.VERSION_CODES.S;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidRefreshRate(float refreshRate) {
        return !Float.isNaN(refreshRate)
                && !Float.isInfinite(refreshRate)
                && refreshRate > 0f;
    }

    private static float highestValidRefreshRate(@Nullable float[] refreshRates) {
        float best = 0f;
        if (refreshRates == null) return best;
        for (float refreshRate : refreshRates) {
            if (isValidRefreshRate(refreshRate)) {
                best = Math.max(best, refreshRate);
            }
        }
        return best;
    }
}
