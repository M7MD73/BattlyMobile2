package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.view.KeyEvent;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.LauncherActivity;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.kdt.pojavlaunch.utils.BattlyAccessDecision.State.*;

public class BattlyAccessManagerTest {
    @Test public void knownBansSurviveOfflineButVerifiedUnbanClearsOnlyThatAccount() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("battly_access_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        try {
            BattlyAccessDecision banned = new BattlyAccessDecision(BANNED, "account", "Fixture only");
            BattlyAccessDecision unavailable = new BattlyAccessDecision(UNAVAILABLE, "", "");
            BattlyAccessDecision allowed = new BattlyAccessDecision(ALLOWED, "", "");
            BattlyAccessManager.remember(prefs, "TestPlayer", banned);
            BattlyAccessManager.remember(prefs, "Another", allowed);
            assertEquals(BANNED, BattlyAccessManager.remember(prefs, "testplayer", unavailable).state);
            assertEquals(UNAVAILABLE, BattlyAccessManager.remember(prefs, "Another", unavailable).state);
            BattlyAccessManager.remember(prefs, "testplayer", allowed);
            assertEquals(UNAVAILABLE, BattlyAccessManager.remember(prefs, "TestPlayer", unavailable).state);
            BattlyAccessManager.remember(prefs, "TestPlayer", new BattlyAccessDecision(BANNED, "device", "Device fixture"));
            assertEquals(BANNED, BattlyAccessManager.remember(prefs, "Another", unavailable).state);
            assertEquals(BANNED, BattlyAccessManager.remember(prefs, "Another", allowed).state);
            BattlyAccessDecision checkedDevice = BattlyAccessDecision.parse(200, new org.json.JSONObject()
                    .put("allowed", true).put("banned", false).put("deviceChecked", true));
            assertEquals(ALLOWED, BattlyAccessManager.remember(prefs, "Another", checkedDevice).state);
            BattlyAccessManager.remember(prefs, "TestPlayer", new BattlyAccessDecision(BANNED, "ip", "IP fixture"));
            assertEquals(BANNED, BattlyAccessManager.remember(prefs, "Another", unavailable).state);
            BattlyAccessManager.remember(prefs, "Another", allowed);
            assertEquals(UNAVAILABLE, BattlyAccessManager.remember(prefs, "TestPlayer", unavailable).state);
        } finally { prefs.edit().clear().commit(); }
    }

    @Test public void suspensionDialogRendersWithoutFinishingActivityOrDeletingProfiles() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),
                LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        try {
            instrumentation.runOnMainSync(() -> BattlyAccessManager.showBlocked(activity,
                    new BattlyAccessDecision(BANNED, "account", "QA: bloqueo simulado; ninguna cuenta real baneada."), () -> {}));
            instrumentation.waitForIdleSync();
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            instrumentation.waitForIdleSync();
            assertFalse(activity.isFinishing());
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                    new java.io.File(activity.getExternalFilesDir(null), "battly-ban-qa.png"))) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            screenshot.recycle();
        } finally {
            instrumentation.runOnMainSync(() -> { BattlyAccessManager.release(activity); activity.finish(); });
        }
    }
}
