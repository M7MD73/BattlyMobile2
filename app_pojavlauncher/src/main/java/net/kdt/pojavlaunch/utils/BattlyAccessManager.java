package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AlertDialog;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.WeakHashMap;

public final class BattlyAccessManager {
    private static final String ENDPOINT = "https://api.battlylauncher.com/api/v2/launcher/access";
    private static final WeakHashMap<Activity, AlertDialog> DIALOGS = new WeakHashMap<>();
    private static final Object CHECK_LOCK = new Object();
    private static final java.util.concurrent.atomic.AtomicLong REVISION = new java.util.concurrent.atomic.AtomicLong();

    private BattlyAccessManager() {}

    public static String deviceClaim(Context context) {
        return context == null ? "" : BattlyDeviceIdentity.claim(context);
    }

    public static void attachDeviceIdentity(HttpURLConnection connection) {
        attachDeviceIdentity(PojavApplication.getAppContext(), connection);
    }

    static void attachDeviceIdentity(Context context, HttpURLConnection connection) {
        // Never attach an app-scoped identifier to an arbitrary third-party URL.
        if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())
                || !"api.battlylauncher.com".equalsIgnoreCase(connection.getURL().getHost())) return;
        String claim = deviceClaim(context);
        if (!claim.isEmpty()) connection.setRequestProperty("X-Battly-Device", claim);
    }

    public static void checkFromPush(Context context) {
        // A push is only a wake-up signal; never trust a push payload as a ban decision.
        PojavApplication.sExecutorService.execute(() -> {
            MinecraftAccount account = PojavProfile.getCurrentProfileContent(context, null);
            BattlyAccessDecision decision = check(context, account == null ? "" : account.username, tokenFor(account));
            if (decision.state == BattlyAccessDecision.State.BANNED) notifyBlocked(context, decision);
            // FCM and Minecraft run in different Android processes. Broadcast only a
            // wake-up; each foreground process revalidates the decision over HTTPS.
            context.sendBroadcast(new android.content.Intent(BattlyAccessRealtime.ACTION_ACCESS_CHANGED)
                    .setPackage(context.getPackageName()));
        });
    }

    private static void notifyBlocked(Context context, BattlyAccessDecision decision) {
        try {
            Tools.buildNotificationChannel(context);
            android.content.Intent intent = new android.content.Intent(context, net.kdt.pojavlaunch.LauncherActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            android.app.PendingIntent pending = android.app.PendingIntent.getActivity(context, 4071, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            androidx.core.app.NotificationCompat.Builder notification = new androidx.core.app.NotificationCompat.Builder(context,
                    context.getString(R.string.notif_channel_id)).setSmallIcon(R.drawable.notif_icon)
                    .setContentTitle(context.getString(R.string.battly_access_suspended))
                    .setContentText(decision.reason).setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(decision.reason))
                    .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true);
            ((android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(4071, notification.build());
        } catch (SecurityException ignored) { /* Notifications may be disabled; access is still blocked. */ }
    }

    public static void release(Activity activity) {
        BattlyDeviceIdentity.release(activity);
        stopWatching(activity);
        AlertDialog dialog = DIALOGS.remove(activity);
        if (dialog != null) dialog.dismiss();
    }

    static String accountKey(String username) {
        return "account:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    public static void checkOnResume(Activity activity) {
        BattlyAccessRealtime.start(activity);
        guard(activity, () -> {}, () -> {});
    }

    public static void stopWatching(Activity activity) {
        BattlyAccessRealtime.stop(activity);
    }

    static void receiveBan(Activity activity, String username, String token, BattlyAccessDecision decision) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (!token.equals(tokenFor(PojavProfile.getCurrentProfileContent(activity, null)))) return;
        REVISION.incrementAndGet();
        Context context = activity.getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            synchronized (CHECK_LOCK) {
                remember(context.getSharedPreferences("battly_access", Context.MODE_PRIVATE), username, decision);
            }
        });
        showBlocked(activity, decision, () -> guard(activity, () -> {}, () -> {}));
    }

    public static void guard(Activity activity, Runnable allowed, Runnable denied) {
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(activity, null);
        String token = tokenFor(account);
        String username = token.isEmpty() || account == null ? "" : account.username;
        checkAsync(activity, username, token, false, allowed, denied);
    }

    public static void guardLogin(Activity activity, String username, String token,
                                  Runnable allowed, Runnable denied) {
        checkAsync(activity, username, token == null ? "" : token, true, allowed, denied);
    }

    static String tokenFor(MinecraftAccount account) {
        if (account == null || account.accessToken == null || account.isMicrosoft
                || account.isDemo() || account.accessToken.equals("0")
                || account.accessToken.equals("battly")) return "";
        return account.accessToken.trim();
    }

    private static void checkAsync(Activity activity, String username, String token, boolean login,
                                   Runnable allowed, Runnable denied) {
        if (!token.isEmpty() && !BattlyDeviceIdentity.accepted(activity)) {
            activity.runOnUiThread(() -> BattlyDeviceIdentity.explain(activity,
                    () -> checkAsync(activity, username, token, login, allowed, denied), denied));
            return;
        }
        Context context = activity.getApplicationContext();
        long revision = REVISION.get();
        PojavApplication.sExecutorService.execute(() -> {
            BattlyAccessDecision decision = login && token.isEmpty()
                    ? new BattlyAccessDecision(BattlyAccessDecision.State.LOGIN_REQUIRED, "", "")
                    : check(context, username, token);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (revision != REVISION.get()) {
                    denied.run();
                    return;
                }
                if (!login && !token.equals(tokenFor(PojavProfile.getCurrentProfileContent(context, null)))) {
                    denied.run();
                    return;
                }
                boolean proceed = decision.state == BattlyAccessDecision.State.ALLOWED
                        || (!login && decision.state == BattlyAccessDecision.State.UNAVAILABLE);
                if (proceed) {
                    BattlyAccessRealtime.reconnect(activity);
                    AlertDialog previous = DIALOGS.remove(activity);
                    if (previous != null) previous.dismiss();
                    allowed.run();
                } else {
                    denied.run();
                    showBlocked(activity, decision, () -> checkAsync(activity, username, token, login, allowed, denied));
                }
            });
        });
    }

    static BattlyAccessDecision check(Context context, String username, String token) {
        synchronized (CHECK_LOCK) {
            SharedPreferences prefs = context.getSharedPreferences("battly_access", Context.MODE_PRIVATE);
            BattlyAccessDecision result = BattlyAccessDecision.parse(0, null);
            if (!BattlyOfflineMode.isOffline(context)) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(7000);
                    connection.setUseCaches(false);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    connection.setRequestProperty("Accept", "application/json");
                    String device = BattlyDeviceIdentity.claim(context);
                    if (!device.isEmpty()) connection.setRequestProperty("X-Battly-Device", device);
                    if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
                    int status = connection.getResponseCode();
                    try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        if (input != null) {
                            byte[] bytes = new byte[1024];
                            int count;
                            while ((count = input.read(bytes)) != -1) {
                                if (output.size() + count > 16384) throw new java.io.IOException("Access response too large");
                                output.write(bytes, 0, count);
                            }
                            result = BattlyAccessDecision.parse(status,
                                    new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8)));
                        }
                    }
                } catch (Exception ignored) {
                    // Preserve confirmed restrictions during outages; never invent a ban.
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            return remember(prefs, username, result);
        }
    }

    static BattlyAccessDecision remember(SharedPreferences prefs, String username, BattlyAccessDecision result) {
        String key = accountKey(username);
        if (result.state == BattlyAccessDecision.State.ALLOWED) {
            SharedPreferences.Editor editor = prefs.edit().remove("ip").remove(key);
            if (result.deviceChecked) editor.remove("device");
            editor.commit();
            if (prefs.contains("device")) return new BattlyAccessDecision(
                    BattlyAccessDecision.State.BANNED, "device", prefs.getString("device", ""));
            return result;
        }
        if (result.state == BattlyAccessDecision.State.BANNED) {
            prefs.edit().putString("device".equals(result.scope) ? "device" :
                    "ip".equals(result.scope) ? "ip" : key, result.reason).commit();
            return result;
        }
        if (prefs.contains("device")) return new BattlyAccessDecision(
                BattlyAccessDecision.State.BANNED, "device", prefs.getString("device", ""));
        if (prefs.contains("ip")) return new BattlyAccessDecision(
                BattlyAccessDecision.State.BANNED, "ip", prefs.getString("ip", ""));
        if (prefs.contains(key)) return new BattlyAccessDecision(
                BattlyAccessDecision.State.BANNED, "account", prefs.getString(key, ""));
        return result;
    }

    static void showBlocked(Activity activity, BattlyAccessDecision decision, Runnable retry) {
        if (decision.state == BattlyAccessDecision.State.BANNED) {
            net.kdt.pojavlaunch.battlyworlds.BattlyWorldsManager.setWaiting(activity, true);
            net.kdt.pojavlaunch.battlyworlds.BattlyWorldsManager.disconnectRealtime();
            net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites.stopPublicRoomHeartbeat();
            net.kdt.pojavlaunch.battlyworlds.BattlyWorldsPreferences.clearActiveRoomCode(activity);
            if (activity instanceof net.kdt.pojavlaunch.MainActivity
                    && org.lwjgl.glfw.CallbackBridge.isGrabbing()) {
                org.lwjgl.glfw.CallbackBridge.sendKeyPress(net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
            }
            notifyBlocked(activity, decision);
        }
        AlertDialog previous = DIALOGS.get(activity);
        if (previous != null && previous.isShowing()) {
            previous.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            return;
        }
        String message = decision.state == BattlyAccessDecision.State.BANNED
                ? activity.getString(R.string.battly_access_suspended)
                : activity.getString(decision.state == BattlyAccessDecision.State.LOGIN_REQUIRED
                ? R.string.login_again : R.string.battly_login_error_network);
        if (!decision.reason.isEmpty()) message += "\n\n" + decision.reason;
        AlertDialog.Builder builder = Tools.createStyledDialogBuilder(activity)
                .setTitle(R.string.global_error).setMessage(message).setCancelable(false)
                .setPositiveButton(R.string.global_retry, null)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    if (decision.state == BattlyAccessDecision.State.BANNED) activity.finishAffinity();
                    else activity.moveTaskToBack(true);
                });
        if (activity instanceof net.kdt.pojavlaunch.LauncherActivity
                && decision.state == BattlyAccessDecision.State.LOGIN_REQUIRED) {
            builder.setNeutralButton(R.string.login_again, (d, which) -> Tools.swapFragment(
                    (net.kdt.pojavlaunch.LauncherActivity) activity,
                    net.kdt.pojavlaunch.fragments.SelectAuthFragment.class,
                    net.kdt.pojavlaunch.fragments.SelectAuthFragment.TAG, null));
        }
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> DIALOGS.remove(activity));
        DIALOGS.put(activity, dialog);
        Tools.styleDialog(dialog);
        dialog.show();
        // Keep the blocking surface in place while the server rechecks access.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            view.setEnabled(false);
            retry.run();
        });
    }
}
