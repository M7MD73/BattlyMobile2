package net.kdt.pojavlaunch.utils;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.appcompat.view.ContextThemeWrapper;
import net.kdt.pojavlaunch.R;
import org.junit.Test;
import static org.junit.Assert.*;

public class NewFeatureBadgeTest {
    @Test public void localizedBadgeWrapsAndDoesNotDuplicateOrCoverControls() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            ContextThemeWrapper context = new ContextThemeWrapper(instrumentation.getTargetContext(),
                    androidx.appcompat.R.style.Theme_AppCompat);
            assertTrue(NewFeatureBadge.isNewPreference("cursorStyle"));
            assertTrue(NewFeatureBadge.isNewPreference("bundled_native_dependencies"));
            assertFalse(NewFeatureBadge.isNewPreference("mousescale"));
            assertFalse(NewFeatureBadge.isNewPreference(null));
            TextView label = new TextView(context);
            label.setText("Personalizar el cursor del juego");
            label.setTextSize(20);
            NewFeatureBadge.apply(label);
            CharSequence once = label.getText();
            NewFeatureBadge.apply(label);
            assertEquals(once.toString(), label.getText().toString());
            assertTrue(label.getText().toString().endsWith(context.getString(R.string.battly_new_badge)));
            assertEquals(1, ((Spanned) label.getText()).getSpans(0, label.length(), ReplacementSpan.class).length);
            int width = (int) (180 * context.getResources().getDisplayMetrics().density);
            label.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            label.layout(0, 0, width, label.getMeasuredHeight());
            assertTrue(label.getLineCount() > 1);
            Bitmap bitmap = Bitmap.createBitmap(width, label.getHeight(), Bitmap.Config.ARGB_8888);
            label.draw(new Canvas(bitmap));
            int mint = 0;
            for (int y = 0; y < bitmap.getHeight(); y++) {
                for (int x = 0; x < width; x++) if (bitmap.getPixel(x, y) == 0xFF79E0C1) mint++;
            }
            assertTrue("Release badge must actually render", mint > 20);
            bitmap.recycle();
            View social = LayoutInflater.from(context).inflate(R.layout.fragment_battly_social, null);
            social.measure(View.MeasureSpec.makeMeasureSpec(width * 3, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(width * 2, View.MeasureSpec.EXACTLY));
            social.layout(0, 0, social.getMeasuredWidth(), social.getMeasuredHeight());
            View button = social.findViewById(R.id.social_stats);
            TextView badge = social.findViewById(R.id.social_stats_new_badge);
            assertTrue(button.getHeight() > 0);
            assertTrue("Badge must not obscure button", badge.getTop() >= button.getBottom());
            assertFalse(badge.isClickable());
            View stats = LayoutInflater.from(context).inflate(R.layout.dialog_battly_usage_stats, null);
            assertNotNull(stats.findViewById(R.id.battly_stats_daily_chart));
        });
    }
}
