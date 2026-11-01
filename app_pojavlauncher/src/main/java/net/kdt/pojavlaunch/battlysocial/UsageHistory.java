package net.kdt.pojavlaunch.battlysocial;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calendar days use the device's local clock at recording time, not UTC. */
public final class UsageHistory {
    public final boolean available;
    public final List<Day> days;

    UsageHistory(JSONObject json) {
        available = json != null && json.optJSONArray("days") != null;
        List<Day> parsed = new ArrayList<>();
        JSONArray array = json == null ? null : json.optJSONArray("days");
        if (array != null) for (int i = 0; i < Math.min(90, array.length()); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row != null && row.optString("date").matches("\\d{4}-\\d{2}-\\d{2}")) {
                parsed.add(new Day(row));
            }
        }
        Collections.sort(parsed, (a, b) -> b.date.compareTo(a.date));
        days = Collections.unmodifiableList(parsed);
    }

    public List<Day> lastDays(int count) {
        return days.subList(0, Math.min(Math.max(0, count), days.size()));
    }

    public static final class Day {
        public final String date;
        public final long game, launcher, world, server, battlyWorlds, menu;
        public final int firstSecond, lastSecond;
        public final long[] gameHours = new long[24], launcherHours = new long[24];

        Day(JSONObject json) {
            date = json.optString("date");
            game = positive(json, "game");
            launcher = positive(json, "launcher");
            world = positive(json, "world");
            server = positive(json, "server");
            battlyWorlds = positive(json, "battlyworlds");
            menu = positive(json, "menu");
            firstSecond = json.isNull("firstSecond") ? -1 : Math.max(0, Math.min(86400, json.optInt("firstSecond", -1)));
            lastSecond = json.isNull("lastSecond") ? -1 : Math.max(0, Math.min(86400, json.optInt("lastSecond", -1)));
            JSONArray hours = json.optJSONArray("hours");
            if (hours != null) for (int i = 0; i < hours.length(); i++) {
                JSONObject hour = hours.optJSONObject(i);
                if (hour == null) continue;
                int index = hour.optInt("hour", -1);
                if (index < 0 || index >= 24) continue;
                gameHours[index] = positive(hour, "game");
                launcherHours[index] = positive(hour, "launcher");
            }
        }

        public boolean active() { return game > 0 || launcher > 0; }
        private static long positive(JSONObject json, String key) {
            return Math.max(0, json.optLong(key, 0));
        }
    }
}
