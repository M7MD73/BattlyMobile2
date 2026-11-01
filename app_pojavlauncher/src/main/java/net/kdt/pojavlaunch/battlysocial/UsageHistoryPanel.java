package net.kdt.pojavlaunch.battlysocial;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import net.kdt.pojavlaunch.R;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Local filtering keeps period/day navigation instant and avoids repeated API calls. */
public final class UsageHistoryPanel {
    private final View root;
    private final Context context;
    private final UsageHistory history;
    private List<UsageHistory.Day> visibleDays;

    public UsageHistoryPanel(View root, UsageHistory history) {
        this.root = root;
        this.context = root.getContext();
        this.history = history;
    }

    public void bind() {
        Spinner period = root.findViewById(R.id.battly_stats_period);
        Spinner day = root.findViewById(R.id.battly_stats_day);
        if (!history.available) {
            text(R.id.battly_stats_history_notice, context.getString(R.string.battly_stats_history_unavailable));
            period.setVisibility(View.GONE);
            day.setVisibility(View.GONE);
            root.findViewById(R.id.battly_stats_daily_chart).setVisibility(View.GONE);
            root.findViewById(R.id.battly_stats_hourly_chart).setVisibility(View.GONE);
            text(R.id.battly_stats_history_summary, "");
            text(R.id.battly_stats_chart_selection, "");
            text(R.id.battly_stats_hour_selection, "");
            text(R.id.battly_stats_day_detail, "");
            return;
        }
        String[] labels = new String[3];
        int[] periods = {7, 30, 90};
        for (int i = 0; i < periods.length; i++) labels[i] = context.getString(R.string.battly_stats_period_days, periods[i]);
        period.setAdapter(adapter(labels));
        day.setOnItemSelectedListener(selected(position -> showDay(visibleDays.get(position))));
        period.setOnItemSelectedListener(selected(position -> showPeriod(periods[position])));
        period.setSelection(1);
    }

    private void showPeriod(int count) {
        visibleDays = history.lastDays(count);
        int active = 0;
        long game = 0, launcher = 0;
        List<String> labels = new ArrayList<>();
        for (UsageHistory.Day day : visibleDays) {
            if (day.active()) active++;
            game += day.game;
            launcher += day.launcher;
            labels.add(context.getString(R.string.battly_stats_day_entry, date(day.date), duration(day.game + day.launcher)));
        }
        text(R.id.battly_stats_history_summary, context.getString(R.string.battly_stats_period_summary,
                active, duration(game), duration(launcher)));
        Spinner days = root.findViewById(R.id.battly_stats_day);
        days.setAdapter(adapter(labels.toArray(new String[0])));
        int size = visibleDays.size();
        long[] games = new long[size], launchers = new long[size];
        String[] dates = new String[size], descriptions = new String[size];
        for (int i = 0; i < size; i++) {
            UsageHistory.Day day = visibleDays.get(size - i - 1);
            games[i] = day.game;
            launchers[i] = day.launcher;
            dates[i] = shortDate(day.date);
            descriptions[i] = dayDescription(day);
        }
        ((UsageChartView) root.findViewById(R.id.battly_stats_daily_chart)).setSeries(games, launchers,
                dates, descriptions, false, index -> days.setSelection(size - index - 1));
        if (visibleDays.isEmpty()) {
            text(R.id.battly_stats_day_detail, context.getString(R.string.battly_stats_empty));
            root.findViewById(R.id.battly_stats_hourly_chart).setVisibility(View.GONE);
        } else showDay(visibleDays.get(0));
    }

    private void showDay(UsageHistory.Day day) {
        ((UsageChartView) root.findViewById(R.id.battly_stats_daily_chart))
                .selectIndex(visibleDays.size() - visibleDays.indexOf(day) - 1);
        text(R.id.battly_stats_chart_selection, dayDescription(day));
        UsageChartView hours = root.findViewById(R.id.battly_stats_hourly_chart);
        hours.setVisibility(View.VISIBLE);
        String[] labels = new String[24], descriptions = new String[24];
        int peakHour = 0;
        for (int i = 0; i < 24; i++) {
            labels[i] = clock(i * 3600);
            descriptions[i] = context.getString(R.string.battly_stats_hour_entry,
                    labels[i], clock((i + 1) * 3600), duration(day.gameHours[i]), duration(day.launcherHours[i]));
            if (day.gameHours[i] + day.launcherHours[i] > day.gameHours[peakHour] + day.launcherHours[peakHour]) peakHour = i;
        }
        hours.setSeries(day.gameHours, day.launcherHours, labels, descriptions, true,
                index -> text(R.id.battly_stats_hour_selection, descriptions[index]));
        hours.selectIndex(peakHour);
        text(R.id.battly_stats_hour_selection, descriptions[peakHour]);
        if (!day.active()) {
            text(R.id.battly_stats_day_detail, context.getString(R.string.battly_stats_empty));
            return;
        }
        String range = day.firstSecond < 0 || day.lastSecond < 0 ? "" :
                context.getString(R.string.battly_stats_time_range, clock(day.firstSecond), clock(day.lastSecond)) + "\n";
        text(R.id.battly_stats_day_detail, range + context.getString(R.string.battly_stats_day_modes,
                duration(day.world), duration(day.server), duration(day.battlyWorlds), duration(day.menu)));
    }

    private String dayDescription(UsageHistory.Day day) {
        return context.getString(R.string.battly_stats_chart_value, date(day.date), duration(day.game), duration(day.launcher));
    }

    private String shortDate(String iso) {
        return iso.substring(8) + "/" + iso.substring(5, 7);
    }

    private String duration(long seconds) {
        if (seconds < 60) return context.getString(R.string.battly_stats_duration_seconds, seconds);
        if (seconds < 3600) return context.getString(R.string.battly_stats_duration_minutes, seconds / 60);
        return context.getString(R.string.battly_stats_duration_hours, seconds / 3600, seconds % 3600 / 60);
    }

    private String date(String iso) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            DateFormat display = DateFormat.getDateInstance(DateFormat.MEDIUM);
            display.setTimeZone(TimeZone.getTimeZone("UTC"));
            return display.format(parser.parse(iso));
        } catch (Exception ignored) { return iso; }
    }

    private String clock(int seconds) {
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 3600, seconds % 3600 / 60);
    }

    private ArrayAdapter<String> adapter(String[] labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private interface Selection { void select(int index); }
    private AdapterView.OnItemSelectedListener selected(Selection action) {
        return new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { action.select(position); }
            public void onNothingSelected(AdapterView<?> parent) { }
        };
    }
    private void text(int id, String value) { ((TextView) root.findViewById(id)).setText(value); }
}
