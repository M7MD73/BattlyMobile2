package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlButton;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlPerformanceWidget;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlVoiceToggleButton;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ControlWidgetRefreshDeviceTest {
    @Test public void unchangedWidgetsDoNotRewriteTextAndEditsRefreshCorrectly() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),
                LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            ControlLayout layout = new ControlLayout(activity) {
                @Override public float getLayoutScale() { return 100f; }
            };
            try {
                verify(new ControlVoiceToggleButton(layout, ControlData.createVoiceMicrophoneWidget()), "updateState");
                verify(new ControlPerformanceWidget(layout, ControlData.createPerformanceWidget()), "updateStatsText");
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    private static void verify(ControlButton widget, String methodName) throws Exception {
        Method refresh = widget.getClass().getDeclaredMethod(methodName);
        refresh.setAccessible(true);
        String initial = widget.getText().toString();
        AtomicInteger updates = new AtomicInteger();
        widget.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { updates.incrementAndGet(); }
            public void afterTextChanged(Editable value) {}
        });
        for (int i = 0; i < 100; i++) refresh.invoke(widget);
        assertEquals(0, updates.get());
        widget.setProperties(widget.getProperties(), false);
        refresh.invoke(widget);
        assertEquals(initial, widget.getText().toString());
        assertTrue(updates.get() > 0);
    }
}
