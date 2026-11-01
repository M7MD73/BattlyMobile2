package org.libsdl.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.FrameLayout;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.customcontrols.keyboard.GameKeyboardPreferences;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutomaticKeyboardPolicyDeviceTest {
    @Test public void queuedOldHideCannotCloseNewlyFocusedInput() {
        android.app.Instrumentation i = InstrumentationRegistry.getInstrumentation();
        Activity activity = i.startActivitySync(new Intent(i.getTargetContext(), LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            i.runOnMainSync(() -> {
                SDLActivity.bindExternalHost(activity, new FrameLayout(activity));
                Thread oldHide = new Thread(() -> SdlImeController.requestHide(SdlImeController.Source.GAME));
                oldHide.start();
                try { oldHide.join(1000); } catch (InterruptedException e) { throw new AssertionError(e); }
                assertFalse(oldHide.isAlive());
                SdlImeController.requestShow(SdlImeController.Source.LAUNCHER);
            });
            i.waitForIdleSync();
            i.runOnMainSync(() -> assertTrue(SdlImeController.isKeyboardActive()));
        } finally { i.runOnMainSync(SdlImeController::reset); }
    }

    @Test public void disablingAutomaticOpeningStillAllowsManualOpening() {
        android.app.Instrumentation i = InstrumentationRegistry.getInstrumentation();
        Activity activity = i.startActivitySync(new Intent(i.getTargetContext(), LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean existed = prefs.contains(GameKeyboardPreferences.AUTO_OPEN);
        boolean previous = prefs.getBoolean(GameKeyboardPreferences.AUTO_OPEN, true);
        i.runOnMainSync(() -> {
            try {
                prefs.edit().putBoolean(GameKeyboardPreferences.AUTO_OPEN, false).commit();
                SDLActivity.bindExternalHost(activity, new FrameLayout(activity));
                assertTrue(SdlImeController.requestShow(SdlImeController.Source.GAME));
                assertFalse(SdlImeController.isKeyboardActive());
                assertTrue(SdlImeController.isTextInputAccepted());
                assertTrue(SdlImeController.requestShow(SdlImeController.Source.LAUNCHER));
                assertTrue(SdlImeController.isKeyboardActive());
            } finally {
                SdlImeController.reset();
                SharedPreferences.Editor editor = prefs.edit();
                if (existed) editor.putBoolean(GameKeyboardPreferences.AUTO_OPEN, previous);
                else editor.remove(GameKeyboardPreferences.AUTO_OPEN);
                editor.commit();
            }
        });
    }
}
