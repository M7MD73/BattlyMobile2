package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;

/** App-scoped identifier. The raw Android ID is neither stored nor transmitted. */
final class BattlyDeviceIdentity {
    private static final String PREFS = "battly_device_privacy";
    private static final WeakHashMap<Activity, Pending> NOTICES = new WeakHashMap<>();

    static boolean accepted(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("device_security_v1", false);
    }

    static String claim(Context context) {
        if (!accepted(context)) return "";
        try { return hashAndroidId(Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID)); }
        catch (RuntimeException error) { return ""; }
    }

    static String hashAndroidId(String value) {
        if (value == null || !value.matches("(?i)[a-f0-9]{1,16}")) return "";
        String normalized = value.toLowerCase(Locale.ROOT);
        normalized = ("0000000000000000" + normalized).substring(normalized.length());
        // Some old Android devices report the same broken ID. Never ban that shared value.
        if (normalized.equals("0000000000000000") || normalized.equals("9774d56d682e549c")) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("battly-mobile-device-v1\0" + normalized).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("v1:");
            for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static void explain(Activity activity, Runnable proceed, Runnable denied) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (accepted(activity)) { proceed.run(); return; }
        Pending pending = NOTICES.get(activity);
        if (pending != null) { pending.allowed.add(proceed); pending.denied.add(denied); return; }
        Pending created = new Pending();
        created.allowed.add(proceed); created.denied.add(denied);
        created.dialog = Tools.createStyledDialogBuilder(activity)
                .setTitle(R.string.battly_device_privacy_title)
                .setMessage(R.string.battly_device_privacy_message).setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putBoolean("device_security_v1", true).apply();
                    NOTICES.remove(activity);
                    BattlyAccessRealtime.stop(activity);
                    BattlyAccessRealtime.start(activity);
                    for (Runnable action : created.allowed) action.run();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    NOTICES.remove(activity);
                    for (Runnable action : created.denied) action.run();
                    activity.moveTaskToBack(true);
                }).create();
        NOTICES.put(activity, created);
        Tools.styleDialog(created.dialog);
        created.dialog.show();
    }

    static void release(Activity activity) {
        Pending pending = NOTICES.remove(activity);
        if (pending != null) pending.dialog.dismiss();
    }

    private static final class Pending {
        final ArrayList<Runnable> allowed = new ArrayList<>();
        final ArrayList<Runnable> denied = new ArrayList<>();
        AlertDialog dialog;
    }
}
