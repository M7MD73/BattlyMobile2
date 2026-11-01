package net.kdt.pojavlaunch.battlysocial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONObject;
import org.junit.Test;

public class BattlyUsageStatsManagerTest {
    @Test
    public void buildsLauncherPayloadWithoutGameMetadata() {
        JSONObject payload = BattlyUsageStatsManager.payload(
                "launcher", "menu", "", "", false);

        assertEquals("launcher", payload.optString("kind"));
        assertEquals("menu", payload.optString("mode"));
        assertFalse(payload.optBoolean("ended"));
    }

    @Test
    public void gamePayloadRetainsVersionAndInstance() {
        JSONObject payload = BattlyUsageStatsManager.payload(
                "game", "world", "1.21.11", "Survival", true);

        assertEquals("game", payload.optString("kind"));
        assertEquals("world", payload.optString("mode"));
        assertEquals("1.21.11", payload.optString("version"));
        assertEquals("Survival", payload.optString("instance"));
        assertEquals(true, payload.optBoolean("ended"));
    }
}
