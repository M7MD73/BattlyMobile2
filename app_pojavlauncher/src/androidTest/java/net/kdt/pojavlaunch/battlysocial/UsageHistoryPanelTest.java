package net.kdt.pojavlaunch.battlysocial;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Exercises the real native layout without sending synthetic playtime to production. */
public class UsageHistoryPanelTest {
    @Test public void periodsDaysAndFallbackRenderOnDevice() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        JSONArray days = new JSONArray();
        for (int i = 1; i <= 22; i++) {
            JSONObject day = new JSONObject().put("date", String.format(java.util.Locale.ROOT, "2026-09-%02d", i));
            if (i < 21) day.put("game", (i % 7 + 1) * 600).put("launcher", (i % 3 + 1) * 180);
            if (i == 22) day.put("game", 5400).put("launcher", 600).put("world", 3600).put("server", 1800)
                    .put("firstSecond", 36000).put("lastSecond", 42600).put("hours", new JSONArray()
                            .put(new JSONObject().put("hour", 10).put("game", 3000).put("launcher", 600))
                            .put(new JSONObject().put("hour", 11).put("game", 2400)));
            days.put(day);
        }
        UsageHistory history = new UsageHistory(new JSONObject().put("days", days));
        View[] root = new View[1];
        AlertDialog[] dialog = new AlertDialog[1];
        try {
            instrumentation.runOnMainSync(() -> {
                root[0] = activity.getLayoutInflater().inflate(R.layout.dialog_battly_usage_stats, null);
                root[0].findViewById(R.id.battly_stats_loading).setVisibility(View.GONE);
                root[0].findViewById(R.id.battly_stats_content).setVisibility(View.VISIBLE);
                new UsageHistoryPanel(root[0], history).bind();
                dialog[0] = Tools.createStyledDialogBuilder(activity).setView(root[0])
                        .setNegativeButton(android.R.string.ok, null).create();
                Tools.styleDialog(dialog[0]);
                dialog[0].show();
                dialog[0].getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * .88),
                        (int) (activity.getResources().getDisplayMetrics().heightPixels * .88));
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertTrue("Statistics dialog must be in the foreground", dialog[0].getWindow().getDecorView().hasWindowFocus());
                assertEquals(22, ((Spinner) root[0].findViewById(R.id.battly_stats_day)).getCount());
                assertEquals(10, ((UsageChartView) root[0].findViewById(R.id.battly_stats_hourly_chart)).getSelectedIndex());
                assertTrue(root[0].findViewById(R.id.battly_stats_day).getWidth() > 0);
            });
            UsageChartView daily = root[0].findViewById(R.id.battly_stats_daily_chart);
            Bitmap[] frames = new Bitmap[2];
            instrumentation.runOnMainSync(() -> {
                // Restart a series transition so animation is tested, not just its final frame.
                ((Spinner) root[0].findViewById(R.id.battly_stats_period)).setSelection(0);
            });
            instrumentation.waitForIdleSync();
            boolean[] animating = new boolean[1];
            instrumentation.runOnMainSync(() -> {
                animating[0] = daily.isAnimating();
                frames[0] = Bitmap.createBitmap(daily.getWidth(), daily.getHeight(), Bitmap.Config.ARGB_8888);
                daily.draw(new android.graphics.Canvas(frames[0]));
            });
            android.os.SystemClock.sleep(800);
            instrumentation.runOnMainSync(() -> {
                assertFalse("Animation must finish instead of running continuously", daily.isAnimating());
                frames[1] = Bitmap.createBitmap(daily.getWidth(), daily.getHeight(), Bitmap.Config.ARGB_8888);
                daily.draw(new android.graphics.Canvas(frames[1]));
                assertTrue("Chart must have a measured plotting surface", daily.getWidth() > 100 && daily.getHeight() > 100);
                if (animating[0]) assertFalse("Animation must change pixels", frames[0].sameAs(frames[1]));
                int coloredPixels = 0;
                for (int x = 0; x < frames[1].getWidth(); x += 3) for (int y = 0; y < frames[1].getHeight(); y += 3) {
                    int pixel = frames[1].getPixel(x, y);
                    if (android.graphics.Color.green(pixel) > 200 && android.graphics.Color.blue(pixel) > 160
                            && android.graphics.Color.red(pixel) < 130) coloredPixels++;
                }
                assertTrue("Actual series must be rendered", coloredPixels > 30);
                frames[0].recycle(); frames[1].recycle();
                daily.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals(1, ((Spinner) root[0].findViewById(R.id.battly_stats_day)).getSelectedItemPosition());
                ((Spinner) root[0].findViewById(R.id.battly_stats_day)).setSelection(0);
            });
            instrumentation.waitForIdleSync();
            android.os.SystemClock.sleep(800);
            try (FileOutputStream output = new FileOutputStream(new File(activity.getExternalFilesDir(null), "usage-history-ui.png"))) {
                Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
                assertNotNull(screenshot);
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
                screenshot.recycle();
            }
            instrumentation.runOnMainSync(() -> {
                android.widget.ScrollView scroll = (android.widget.ScrollView) root[0];
                android.graphics.Rect bounds = new android.graphics.Rect();
                View day = root[0].findViewById(R.id.battly_stats_daily_chart);
                day.getDrawingRect(bounds);
                scroll.offsetDescendantRectToMyCoords(day, bounds);
                scroll.scrollTo(0, bounds.top);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> assertTrue(root[0].getScrollY() > 0));
            android.os.SystemClock.sleep(200); // Allow the scrolled frame to reach the compositor.
            try (FileOutputStream output = new FileOutputStream(new File(activity.getExternalFilesDir(null), "usage-history-detail-ui.png"))) {
                Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
                assertNotNull(screenshot);
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
                screenshot.recycle();
            }
            instrumentation.runOnMainSync(() -> {
                long now = android.os.SystemClock.uptimeMillis();
                float x = 46 * activity.getResources().getDisplayMetrics().density + 1;
                android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, daily.getHeight() / 2f, 0);
                android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 30, android.view.MotionEvent.ACTION_UP, x, daily.getHeight() / 2f, 0);
                daily.dispatchTouchEvent(down); daily.dispatchTouchEvent(up);
                down.recycle(); up.recycle();
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals("Tapping the first point must select the oldest day", 6,
                        ((Spinner) root[0].findViewById(R.id.battly_stats_day)).getSelectedItemPosition());
                ((Spinner) root[0].findViewById(R.id.battly_stats_day)).setSelection(0);
            });
            instrumentation.waitForIdleSync();
            android.os.SystemClock.sleep(800);
            instrumentation.runOnMainSync(() -> {
                android.widget.ScrollView scroll = (android.widget.ScrollView) root[0];
                View hourly = root[0].findViewById(R.id.battly_stats_hourly_chart);
                android.graphics.Rect bounds = new android.graphics.Rect();
                hourly.getDrawingRect(bounds);
                scroll.offsetDescendantRectToMyCoords(hourly, bounds);
                scroll.scrollTo(0, bounds.top);
            });
            instrumentation.waitForIdleSync();
            try (FileOutputStream output = new FileOutputStream(new File(activity.getExternalFilesDir(null), "usage-history-hourly-ui.png"))) {
                android.os.SystemClock.sleep(200);
                Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
                assertNotNull(screenshot);
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
                screenshot.recycle();
            }
            instrumentation.runOnMainSync(() -> ((Spinner) root[0].findViewById(R.id.battly_stats_period)).setSelection(0));
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals(7, ((Spinner) root[0].findViewById(R.id.battly_stats_day)).getCount());
                ((Spinner) root[0].findViewById(R.id.battly_stats_day)).setSelection(1);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals(activity.getString(R.string.battly_stats_empty),
                        ((TextView) root[0].findViewById(R.id.battly_stats_day_detail)).getText().toString());
                ((Spinner) root[0].findViewById(R.id.battly_stats_period)).setSelection(2);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals(22, ((Spinner) root[0].findViewById(R.id.battly_stats_day)).getCount());
                new UsageHistoryPanel(root[0], new UsageHistory(null)).bind();
                assertEquals(View.GONE, root[0].findViewById(R.id.battly_stats_period).getVisibility());
            });
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (dialog[0] != null) dialog[0].dismiss();
                if (root[0] != null) assertFalse(((UsageChartView) root[0].findViewById(R.id.battly_stats_daily_chart)).isAnimating());
                activity.finish();
            });
        }
    }
}
