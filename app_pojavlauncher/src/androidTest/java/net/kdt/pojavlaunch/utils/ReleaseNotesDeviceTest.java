package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.onboarding.OnboardingActivity;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

public class ReleaseNotesDeviceTest {
    @Test public void currentReleaseHasVisibleNativePanelAndIndependentSeenKey() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        assertEquals("battly_whats_new_seen_" + BuildConfig.VERSION_CODE,
                OnboardingActivity.getWhatsNewSeenPreferenceKey());
        Activity activity = instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), OnboardingActivity.class)
                .putExtra(OnboardingActivity.EXTRA_WHATS_NEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                String title = activity.getString(R.string.battly_whats_new_title);
                assertTrue(title.contains(BuildConfig.VERSION_NAME));
                assertTrue(hasVisibleText(activity.getWindow().getDecorView(), title));
                assertNotNull(activity.findViewById(R.id.onboarding_viewpager));
            });
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            File file = new File(instrumentation.getTargetContext().getExternalFilesDir(null),
                    "release-notes-" + BuildConfig.VERSION_NAME + ".png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
            } finally {
                screenshot.recycle();
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static boolean hasVisibleText(View view, String text) {
        if (view instanceof TextView && view.isShown()
                && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasVisibleText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }
}
