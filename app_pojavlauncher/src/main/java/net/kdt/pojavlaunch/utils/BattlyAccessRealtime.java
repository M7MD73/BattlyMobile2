package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.WeakHashMap;
import io.socket.client.IO;
import io.socket.client.Socket;

/** Foreground access notifications, independent of Social/Worlds invitation settings. */
final class BattlyAccessRealtime {
    static final String ACTION_ACCESS_CHANGED = "net.kdt.pojavlaunch.ACCESS_CHANGED";
    private static final WeakHashMap<Activity, Session> SESSIONS = new WeakHashMap<>();

    static void start(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (BattlyOfflineMode.isOffline(activity)) { stop(activity); return; }
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(activity, null);
        String token = BattlyAccessManager.tokenFor(account);
        Session previous = SESSIONS.get(activity);
        if (previous != null && previous.token.equals(token)) {
            if (!previous.socket.connected()) previous.socket.connect();
            return;
        }
        stop(activity);
        try {
            Session session = new Session(activity, token, account == null ? "" : account.username);
            SESSIONS.put(activity, session);
            session.socket.connect();
        } catch (URISyntaxException error) {
            Log.e("BattlyAccess", "Invalid access notification URL", error);
        }
    }

    static void stop(Activity activity) {
        Session session = SESSIONS.remove(activity);
        if (session == null) return;
        session.preferences.unregisterOnSharedPreferenceChangeListener(session.profileListener);
        Tools.MAIN_HANDLER.removeCallbacks(session.reconcile);
        activity.unregisterReceiver(session.pushReceiver);
        session.socket.off();
        session.socket.disconnect();
    }

    static void reconnect(Activity activity) {
        if (SESSIONS.containsKey(activity)) start(activity);
    }

    private static final class Session {
        final String token;
        final Socket socket;
        final SharedPreferences preferences;
        final SharedPreferences.OnSharedPreferenceChangeListener profileListener;
        long lastRecovery = -30_000;
        final java.util.concurrent.atomic.AtomicBoolean checking = new java.util.concurrent.atomic.AtomicBoolean();
        final Runnable reconcile;
        final android.content.BroadcastReceiver pushReceiver;

        Session(Activity activity, String token, String username) throws URISyntaxException {
            this.token = token;
            HashMap<String, String> auth = new HashMap<>();
            if (!token.isEmpty()) auth.put("token", token);
            String device = BattlyDeviceIdentity.claim(activity);
            if (!device.isEmpty()) auth.put("device", device);
            IO.Options options = IO.Options.builder().setAuth(auth).setForceNew(true)
                    .setReconnection(true).setReconnectionDelay(1500).setReconnectionDelayMax(15000)
                    .setTimeout(10000).build();
            socket = IO.socket("https://api.battlylauncher.com/access", options);
            preferences = PojavProfile.getPrefs(activity);
            profileListener = (prefs, key) -> Tools.MAIN_HANDLER.post(() -> {
                if (SESSIONS.get(activity) == this) {
                    stop(activity);
                    BattlyAccessManager.checkOnResume(activity);
                }
            });
            preferences.registerOnSharedPreferenceChangeListener(profileListener);
            reconcile = new Runnable() {
                @Override public void run() {
                    if (SESSIONS.get(activity) != Session.this) return;
                    checkNow(activity, username);
                    Tools.MAIN_HANDLER.postDelayed(this, 30_000);
                }
            };
            pushReceiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
                    if (ACTION_ACCESS_CHANGED.equals(intent.getAction()) && SESSIONS.get(activity) == Session.this) {
                        checkNow(activity, username);
                    }
                }
            };
            androidx.core.content.ContextCompat.registerReceiver(activity, pushReceiver,
                    new android.content.IntentFilter(ACTION_ACCESS_CHANGED),
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
            Tools.MAIN_HANDLER.postDelayed(reconcile, 30_000);
            socket.on(Socket.EVENT_CONNECT, args -> Tools.MAIN_HANDLER.post(() -> {
                if (SESSIONS.get(activity) == this) checkNow(activity, username);
            }));
            socket.on("battly_banned", args -> receive(activity, username, args));
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                receive(activity, username, args);
                Tools.MAIN_HANDLER.post(() -> {
                    if (SESSIONS.get(activity) != this) return;
                    // Rejected handshakes may omit structured error data on older servers.
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastRecovery >= 30_000) {
                        lastRecovery = now;
                        BattlyAccessManager.guard(activity, () -> {}, () -> {});
                    }
                });
            });
        }

        void checkNow(Activity activity, String username) {
            if (activity.isFinishing() || activity.isDestroyed() || !checking.compareAndSet(false, true)) return;
            android.content.Context context = activity.getApplicationContext();
            net.kdt.pojavlaunch.PojavApplication.sExecutorService.execute(() -> {
                try {
                    BattlyAccessDecision decision = BattlyAccessManager.check(context, username, token);
                    Tools.MAIN_HANDLER.post(() -> {
                        if (SESSIONS.get(activity) == this && decision.state == BattlyAccessDecision.State.BANNED) {
                            BattlyAccessManager.receiveBan(activity, username, token, decision);
                        }
                    });
                } finally {
                    checking.set(false);
                }
            });
        }

        void receive(Activity activity, String username, Object[] args) {
            if (args.length == 0) return;
            BattlyAccessDecision decision = BattlyAccessDecision.fromRealtime(args[0]);
            if (decision.state != BattlyAccessDecision.State.BANNED) return;
            Tools.MAIN_HANDLER.post(() -> {
                if (SESSIONS.get(activity) == this) {
                    BattlyAccessManager.receiveBan(activity, username, token, decision);
                }
            });
        }
    }
}
