package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.net.HttpURLConnection;
import java.net.URL;
import static org.junit.Assert.*;

public class BattlyBanCacheDeviceTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test public void banShowsABlockingPanelAndRetryDoesNotDismissItPrematurely() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        assertFalse(net.kdt.pojavlaunch.battlyworlds.BattlyWorldsManager.isRoomActive());
        android.app.Activity activity = instrumentation.startActivitySync(new android.content.Intent(
                context(), net.kdt.pojavlaunch.onboarding.OnboardingActivity.class)
                .putExtra(net.kdt.pojavlaunch.onboarding.OnboardingActivity.EXTRA_WHATS_NEW, true)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        String previousRoom = net.kdt.pojavlaunch.battlyworlds.BattlyWorldsPreferences.getActiveRoomCode(context());
        java.lang.reflect.Field field = BattlyAccessManager.class.getDeclaredField("DIALOGS");
        field.setAccessible(true);
        java.util.Map<?, ?> dialogs = (java.util.Map<?, ?>) field.get(null);
        java.util.concurrent.atomic.AtomicInteger retried = new java.util.concurrent.atomic.AtomicInteger();
        BattlyAccessDecision decision = ban("account");
        try {
            instrumentation.runOnMainSync(() -> {
                net.kdt.pojavlaunch.battlyworlds.BattlyWorldsPreferences.setActiveRoomCode(activity, "QA1234");
                BattlyAccessManager.showBlocked(activity, decision, retried::incrementAndGet);
                androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) dialogs.get(activity);
                assertNotNull(dialog);
                assertTrue(dialog.isShowing());
                assertEquals("", net.kdt.pojavlaunch.battlyworlds.BattlyWorldsPreferences.getActiveRoomCode(activity));
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(1, retried.get());
                assertTrue(dialog.isShowing());
                assertFalse(dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled());
            });
        } finally {
            instrumentation.runOnMainSync(() -> {
                BattlyAccessManager.release(activity);
                net.kdt.pojavlaunch.battlyworlds.BattlyWorldsPreferences.setActiveRoomCode(activity, previousRoom);
                ((android.app.NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(4071);
                activity.finish();
            });
        }
    }

    @Test public void confirmedBansSurviveOutagesWithoutLeakingToOtherAccounts() throws Exception {
        SharedPreferences prefs = context().getSharedPreferences("ban_qa_cache", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        try {
            BattlyAccessDecision account = ban("account");
            BattlyAccessManager.remember(prefs, "Owner", account);
            BattlyAccessDecision outage = BattlyAccessDecision.parse(503, null);
            assertEquals(BattlyAccessDecision.State.BANNED, BattlyAccessManager.remember(prefs, "owner", outage).state);
            assertEquals(BattlyAccessDecision.State.UNAVAILABLE, BattlyAccessManager.remember(prefs, "Friend", outage).state);
            BattlyAccessManager.remember(prefs, "Owner", ban("device"));
            assertEquals("device", BattlyAccessManager.remember(prefs, "Friend", outage).scope);
            BattlyAccessDecision incompleteAllow = BattlyAccessDecision.parse(200,
                    new JSONObject().put("allowed", true).put("banned", false));
            assertEquals("device", BattlyAccessManager.remember(prefs, "Friend", incompleteAllow).scope);
            BattlyAccessDecision fullAllow = BattlyAccessDecision.parse(200,
                    new JSONObject().put("allowed", true).put("banned", false).put("deviceChecked", true));
            assertEquals(BattlyAccessDecision.State.ALLOWED, BattlyAccessManager.remember(prefs, "Friend", fullAllow).state);
            assertFalse(prefs.contains("device"));
        } finally {
            prefs.edit().clear().commit();
        }
    }

    @Test public void onlyBattlyHttpsReceivesAConsentedHashedDeviceClaim() throws Exception {
        SharedPreferences prefs = context().getSharedPreferences("ban_qa_identity", Context.MODE_PRIVATE);
        Context isolated = new ContextWrapper(context()) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
        };
        prefs.edit().clear().commit();
        try {
            HttpURLConnection noConsent = connection("https://api.battlylauncher.com/api/v2/battlyworlds/moderation/me");
            BattlyAccessManager.attachDeviceIdentity(isolated, noConsent);
            assertNull(noConsent.getRequestProperty("X-Battly-Device"));
            prefs.edit().putBoolean("device_security_v1", true).commit();
            HttpURLConnection trusted = connection("https://api.battlylauncher.com/api/v2/battlyworlds/moderation/me");
            BattlyAccessManager.attachDeviceIdentity(isolated, trusted);
            String claim = BattlyAccessManager.deviceClaim(isolated);
            assertTrue(claim.matches("v1:[a-f0-9]{64}"));
            assertEquals(claim, trusted.getRequestProperty("X-Battly-Device"));
            for (String url : new String[]{"http://api.battlylauncher.com/a", "https://example.com/a",
                    "https://api.battlylauncher.com.example.com/a"}) {
                HttpURLConnection other = connection(url);
                BattlyAccessManager.attachDeviceIdentity(isolated, other);
                assertNull(other.getRequestProperty("X-Battly-Device"));
            }
        } finally {
            prefs.edit().clear().commit();
        }
    }

    private BattlyAccessDecision ban(String scope) throws Exception {
        return BattlyAccessDecision.parse(403, new JSONObject().put("banned", true)
                .put("code", "BATTLY_BANNED").put("scope", scope).put("message", "Isolated QA"));
    }

    private HttpURLConnection connection(String url) throws Exception {
        return new HttpURLConnection(new URL(url)) {
            public void connect() { fail("No network request is allowed in this test"); }
            public void disconnect() {}
            public boolean usingProxy() { return false; }
        };
    }
}
