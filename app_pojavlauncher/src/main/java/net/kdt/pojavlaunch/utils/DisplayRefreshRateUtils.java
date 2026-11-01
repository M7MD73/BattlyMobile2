package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DisplayRefreshRateUtils {
    private static final String TAG = "DisplayRefreshRate";

    private DisplayRefreshRateUtils() {
    }

    @Nullable
    public static Display getDisplay(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Display display = activity.getDisplay();
            if (display != null) return display;
        }
        return activity.getWindowManager().getDefaultDisplay();
    }

    public static float getMaximumSupportedRefreshRate(@Nullable Display display) {
        if (display == null) return 0f;
        Display.Mode currentMode = display.getMode();
        float currentRate = currentMode.getRefreshRate();
        float[] alternatives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? currentMode.getAlternativeRefreshRates() : null;
        float[] supportedRates = ratesAtCurrentResolution(display, currentMode);
        float targetRate = FramePacingPolicy.selectSurfaceFrameRate(currentRate, alternatives,
                supportedRates);
        Log.i(TAG, "Display refresh rates: current=" + currentRate
                + ", supported=" + java.util.Arrays.toString(supportedRates)
                + ", target=" + targetRate);
        return targetRate;
    }

    public static float requestMaximumSupportedRefreshRate(Activity activity) {
        Display display = getDisplay(activity);
        float targetRate = getMaximumSupportedRefreshRate(display);
        if (display == null || targetRate <= 0f) return targetRate;

        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.preferredRefreshRate = targetRate;
        int preferredModeId = preferredModeId(display, display.getMode(), targetRate);
        if (preferredModeId != 0) {
            attributes.preferredDisplayModeId = preferredModeId;
        }
        activity.getWindow().setAttributes(attributes);
        return targetRate;
    }

    private static float[] ratesAtCurrentResolution(Display display, Display.Mode currentMode) {
        List<Float> rates = new ArrayList<>();
        // This API is deprecated in favor of Display.Mode, but Samsung can hide high-refresh
        // modes from getSupportedModes() while power saving is active. It still reports the
        // real rates supported at the current resolution here.
        for (float rate : display.getSupportedRefreshRates()) {
            addUniqueRate(rates, rate);
        }
        for (Display.Mode mode : display.getSupportedModes()) {
            if (sameResolution(mode, currentMode)) addUniqueRate(rates, mode.getRefreshRate());
        }
        float[] result = new float[rates.size()];
        for (int i = 0; i < rates.size(); i++) result[i] = rates.get(i);
        return result;
    }

    private static void addUniqueRate(List<Float> rates, float candidate) {
        if (candidate <= 0f || Float.isNaN(candidate) || Float.isInfinite(candidate)) return;
        for (float rate : rates) {
            if (Math.abs(rate - candidate) < 0.1f) return;
        }
        rates.add(candidate);
    }

    private static int preferredModeId(Display display, Display.Mode currentMode, float targetRate) {
        for (Display.Mode mode : display.getSupportedModes()) {
            if (sameResolution(mode, currentMode)
                    && Math.abs(mode.getRefreshRate() - targetRate) < 0.1f) {
                return mode.getModeId();
            }
        }
        return 0;
    }

    private static boolean sameResolution(Display.Mode first, Display.Mode second) {
        return first.getPhysicalWidth() == second.getPhysicalWidth()
                && first.getPhysicalHeight() == second.getPhysicalHeight();
    }
}
