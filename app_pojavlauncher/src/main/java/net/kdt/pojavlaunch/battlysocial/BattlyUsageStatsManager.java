package net.kdt.pojavlaunch.battlysocial;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsManager;
import net.kdt.pojavlaunch.customcontrols.MinecraftServerSessionTracker;
import net.kdt.pojavlaunch.utils.BattlyOfflineMode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated play-time accounting for Battly accounts on Mobile. */
public final class BattlyUsageStatsManager {
    private static final String TAG = "BattlyUsageStats";
    private static final long HEARTBEAT_INTERVAL_MS = 20_000L;

    private static final Session LAUNCHER = new Session("launcher");
    private static final Session GAME = new Session("game");
    private static Context sLauncherContext;
    private static Context sGameContext;
    private static String sGameVersion = "";
    private static String sGameInstance = "";

    private BattlyUsageStatsManager() {
    }

    public static void startLauncherSession(Context context) {
        if (!canTrack(context)) return;
        sLauncherContext = context.getApplicationContext();
        start(LAUNCHER, sLauncherContext, LAUNCHER_HEARTBEAT);
    }

    public static void stopLauncherSession(Context context) {
        stop(LAUNCHER, context, LAUNCHER_HEARTBEAT, payload("launcher", "menu", "", "", true));
        sLauncherContext = null;
    }

    public static void startGameSession(Context context, String version, String instance) {
        if (!canTrack(context)) return;
        sGameContext = context.getApplicationContext();
        sGameVersion = safe(version);
        sGameInstance = safe(instance);
        start(GAME, sGameContext, GAME_HEARTBEAT);
    }

    public static void stopGameSession(Context context) {
        stop(GAME, context, GAME_HEARTBEAT, gamePayload(true));
        sGameContext = null;
        sGameVersion = "";
        sGameInstance = "";
    }

    private static final Runnable LAUNCHER_HEARTBEAT = new Runnable() {
        @Override
        public void run() {
            Context context = sLauncherContext;
            if (context == null || !LAUNCHER.active) return;
            send(LAUNCHER, context, payload("launcher", "menu", "", "", false));
            Tools.MAIN_HANDLER.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    private static final Runnable GAME_HEARTBEAT = new Runnable() {
        @Override
        public void run() {
            Context context = sGameContext;
            if (context == null || !GAME.active) return;
            send(GAME, context, gamePayload(false));
            Tools.MAIN_HANDLER.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    private static void start(Session session, Context context, Runnable heartbeat) {
        if (session.active) return;
        session.active = true;
        session.sessionId = UUID.randomUUID().toString();
        session.sequence = 0L;
        session.accountToken = BattlySocialApi.usageAccountToken(context);
        Tools.MAIN_HANDLER.removeCallbacks(heartbeat);
        Tools.MAIN_HANDLER.post(heartbeat);
    }

    private static void stop(Session session, Context context, Runnable heartbeat, JSONObject payload) {
        Tools.MAIN_HANDLER.removeCallbacks(heartbeat);
        if (!session.active || context == null) return;
        session.active = false;
        send(session, context.getApplicationContext(), payload);
    }

    private static void send(Session session, Context context, JSONObject payload) {
        if (!canTrack(context)) return;
        final String token = BattlySocialApi.usageAccountToken(context);
        synchronized (session) {
            if (!token.equals(session.accountToken)) {
                session.accountToken = token;
                session.sessionId = UUID.randomUUID().toString();
                session.sequence = 0L;
                session.pendingFinal = null;
            }
            // A queued final packet belongs to its original account/session only.
            if (payload.has("sessionId") && !session.sessionId.equals(payload.optString("sessionId"))) return;
            try {
                if (!payload.has("sessionId")) payload.put("sessionId", session.sessionId);
                if (!payload.has("sequence")) payload.put("sequence", ++session.sequence);
                payload.put("clientVersion", BuildConfig.VERSION_NAME);
            } catch (JSONException exception) {
                return;
            }
        }
        if (!session.inFlight.compareAndSet(false, true)) {
            if (payload.optBoolean("ended", false)) session.pendingFinal = payload;
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlySocialApi.sendUsageHeartbeat(context, payload, token);
            } catch (Throwable error) {
                Log.d(TAG, "Usage heartbeat deferred: " + error.getMessage());
            } finally {
                session.inFlight.set(false);
                JSONObject pending = session.pendingFinal;
                session.pendingFinal = null;
                if (pending != null) send(session, context, pending);
            }
        });
    }

    static JSONObject gamePayload(boolean ended) {
        MinecraftServerSessionTracker.Mode trackerMode = MinecraftServerSessionTracker.getMode();
        boolean battlyWorlds = BattlyWorldsManager.isRoomActive();
        String mode = battlyWorlds ? "battlyworlds"
                : trackerMode == MinecraftServerSessionTracker.Mode.SINGLEPLAYER ? "world"
                : trackerMode == MinecraftServerSessionTracker.Mode.MULTIPLAYER ? "server"
                : "menu";
        JSONObject payload = payload("game", mode, sGameVersion, sGameInstance, ended);
        try {
            if ("world".equals(mode)) {
                payload.put("world", new JSONObject().put("name",
                        emptyFallback(MinecraftServerSessionTracker.getWorldName(), "Mundo local")));
            } else if ("battlyworlds".equals(mode)) {
                payload.put("server", new JSONObject()
                        .put("host", "battlyworlds")
                        .put("port", 25565)
                        .put("name", "Battly Worlds"));
            } else if ("server".equals(mode)) {
                MinecraftServerSessionTracker.Endpoint endpoint = MinecraftServerSessionTracker.getEndpoint();
                if (endpoint != null) {
                    payload.put("server", new JSONObject()
                            .put("host", endpoint.host)
                            .put("port", endpoint.port)
                            .put("name", endpoint.host));
                }
            }
        } catch (JSONException ignored) {
        }
        return payload;
    }

    static JSONObject payload(String kind, String mode, String version, String instance, boolean ended) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("kind", kind);
            payload.put("mode", mode);
            payload.put("version", safe(version));
            payload.put("instance", safe(instance));
            payload.put("ended", ended);
            payload.put("offsetMinutes", java.util.TimeZone.getDefault()
                    .getOffset(System.currentTimeMillis()) / 60000);
        } catch (JSONException ignored) {
        }
        return payload;
    }

    private static boolean canTrack(Context context) {
        return context != null
                && !BattlyOfflineMode.isOffline(context)
                && Tools.isValidString(BattlySocialApi.usageAccountToken(context));
    }

    private static String emptyFallback(String value, String fallback) {
        return Tools.isValidString(value) ? value.trim() : fallback;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Session {
        final String kind;
        final AtomicBoolean inFlight = new AtomicBoolean();
        volatile boolean active;
        volatile String sessionId = "";
        volatile String accountToken = "";
        volatile long sequence;
        volatile JSONObject pendingFinal;

        Session(String kind) {
            this.kind = kind;
        }
    }
}
