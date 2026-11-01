package net.kdt.pojavlaunch.battlysocial;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class UsageHistoryTest {
    @Test public void olderApiDoesNotPretendToHaveHistory() {
        UsageHistory history = new UsageHistory(null);
        assertFalse(history.available);
        assertTrue(history.days.isEmpty());
    }

    @Test public void datesAreNewestFirstAndPeriodsKeepInactiveDays() throws Exception {
        JSONArray days = new JSONArray();
        for (int i = 1; i <= 15; i++) days.put(new JSONObject()
                .put("date", String.format(java.util.Locale.ROOT, "2026-09-%02d", i))
                .put("game", i == 15 ? 20 : 0));
        UsageHistory history = new UsageHistory(new JSONObject().put("days", days));
        assertTrue(history.available);
        assertEquals(7, history.lastDays(7).size());
        assertEquals("2026-09-15", history.days.get(0).date);
        assertTrue(history.days.get(0).active());
        assertFalse(history.days.get(1).active());
        assertEquals(15, history.lastDays(90).size());
    }

    @Test public void hoursKeepGameAndLauncherSeparateAndRejectInvalidIndices() throws Exception {
        UsageHistory history = new UsageHistory(new JSONObject().put("days", new JSONArray()
                .put(new JSONObject().put("date", "2026-09-22").put("firstSecond", JSONObject.NULL)
                        .put("hours", new JSONArray()
                                .put(new JSONObject().put("hour", 12).put("game", 30).put("launcher", 10))
                                .put(new JSONObject().put("hour", 24).put("game", 999))))));
        UsageHistory.Day day = history.days.get(0);
        assertEquals(30, day.gameHours[12]);
        assertEquals(10, day.launcherHours[12]);
        assertEquals(-1, day.firstSecond);
        assertEquals(0, day.gameHours[0]);
    }
}
