package net.kdt.pojavlaunch.battlysocial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class BattlySocialApiTest {
    @Test
    public void usageRequiresTheActiveBattlyAccountNotMicrosoftOrLocal() {
        net.kdt.pojavlaunch.value.MinecraftAccount account = new net.kdt.pojavlaunch.value.MinecraftAccount();
        assertEquals("", BattlySocialApi.usageAccountTokenFor(account));
        account.accessToken = "fixture-token-for-account-scope-test";
        account.isMicrosoft = true;
        assertEquals("", BattlySocialApi.usageAccountTokenFor(account));
        account.isMicrosoft = false;
        assertEquals(account.accessToken, BattlySocialApi.usageAccountTokenFor(account));
        account.accessToken = null;
        assertEquals("", BattlySocialApi.usageAccountTokenFor(account));
    }
    @Test
    public void battlyWorldsPresenceKeepsRoomTypeAndVersion() throws Exception {
        JSONObject payload = BattlySocialApi.buildPresencePayload(
                "playing", "1.21.8", null, true);
        JSONObject activity = payload.getJSONObject("activity");

        assertEquals("playing", payload.getString("state"));
        assertEquals("battlyworlds", activity.getString("type"));
        assertTrue(activity.getBoolean("battlyWorlds"));
        assertEquals("1.21.8", activity.getString("version"));
    }

    @Test
    public void multiplayerPresenceIncludesServerInActivityAndPayload() throws Exception {
        BattlySocialApi.Server server = new BattlySocialApi.Server(new JSONObject()
                .put("host", "play.example.net")
                .put("port", 25570)
                .put("name", "Example"));
        JSONObject payload = BattlySocialApi.buildPresencePayload(
                "playing", "1.20.1-forge", server, false);

        assertEquals("play.example.net", payload.getJSONObject("server").getString("host"));
        assertEquals(25570, payload.getJSONObject("activity")
                .getJSONObject("server").getInt("port"));
    }

    @Test
    public void legacyStatsTreatExistingTotalAsDesktopTime() throws Exception {
        BattlySocialApi.UsageStats stats = new BattlySocialApi.UsageStats(new JSONObject()
                .put("data", new JSONObject()
                        .put("tiempoJugado", 7200)
                        .put("tiempoLauncher", 3600)));

        assertEquals(7200, stats.playedSeconds);
        assertEquals(7200, stats.desktopPlayedSeconds);
        assertEquals(0, stats.mobilePlayedSeconds);
    }

    @Test
    public void combinedStatsKeepDesktopAndMobileBreakdown() throws Exception {
        BattlySocialApi.UsageStats stats = new BattlySocialApi.UsageStats(new JSONObject()
                .put("data", new JSONObject()
                        .put("tiempoJugado", 9000)
                        .put("desktop", new JSONObject().put("tiempoJugado", 7200))
                        .put("mobile", new JSONObject().put("tiempoJugado", 1800))
                        .put("breakdown", new JSONObject()
                                .put("mundos", 600)
                                .put("servidores", 900))));

        assertEquals(9000, stats.playedSeconds);
        assertEquals(7200, stats.desktopPlayedSeconds);
        assertEquals(1800, stats.mobilePlayedSeconds);
        assertEquals(600, stats.worldSeconds);
        assertEquals(900, stats.serverSeconds);
    }
}
