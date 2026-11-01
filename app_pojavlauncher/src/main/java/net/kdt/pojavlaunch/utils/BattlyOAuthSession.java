package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Both loopback and deep-link callbacks compete for the same single-use transaction. */
public final class BattlyOAuthSession {
    private static String inFlightOwner;
    private static Runnable callbackListener;
    private BattlyOAuthSession() { }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences("battly_oauth", Context.MODE_PRIVATE);
    }

    public static synchronized void begin(Context context, String provider, String nonce) {
        inFlightOwner = null;
        preferences(context).edit().clear().putString("pending_provider", provider)
                .putString("pending_nonce", nonce).putLong("started", SystemClock.elapsedRealtime()).commit();
    }

    public static synchronized boolean accept(Context context, String provider, String token, String nonce) {
        SharedPreferences prefs = preferences(context);
        long now = SystemClock.elapsedRealtime();
        if (token == null || token.isEmpty() || token.length() > 16384
                || !OAuthCallbackPolicy.matches(prefs.getString("pending_provider", null),
                prefs.getString("pending_nonce", null), prefs.getLong("started", -1), provider, nonce, now)) {
            return false;
        }
        boolean saved = prefs.edit().clear().putString("accepted_provider", provider)
                .putString("accepted_token", token).putLong("accepted_at", now).commit();
        if (saved && callbackListener != null) net.kdt.pojavlaunch.Tools.MAIN_HANDLER.post(callbackListener);
        return saved;
    }

    public static synchronized void setListener(Runnable listener) { callbackListener = listener; }
    public static synchronized void clearListener(Runnable listener) {
        if (callbackListener == listener) callbackListener = null;
    }

    public static synchronized String claimToken(Context context, String provider, String owner) {
        if (inFlightOwner != null) return null;
        SharedPreferences prefs = preferences(context);
        if (!provider.equals(prefs.getString("accepted_provider", null))) return null;
        long started = prefs.getLong("accepted_at", -1);
        long age = SystemClock.elapsedRealtime() - started;
        if (started < 0 || age < 0 || age > OAuthCallbackPolicy.TIMEOUT_MS) {
            prefs.edit().clear().commit();
            return null;
        }
        String token = prefs.getString("accepted_token", null);
        if (token == null || token.isEmpty()) return null;
        inFlightOwner = owner;
        return token;
    }

    public static synchronized boolean owns(String owner) { return owner.equals(inFlightOwner); }
    public static synchronized void release(String owner) { if (owns(owner)) inFlightOwner = null; }
    public static synchronized void complete(Context context, String owner) {
        if (!owns(owner)) return;
        preferences(context).edit().clear().commit();
        inFlightOwner = null;
    }

}
