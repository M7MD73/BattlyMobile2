package net.kdt.pojavlaunch.customcontrols;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.KeyEvent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.preference.PreferenceManager;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.mouse.CapturedMouseMotion;
import net.kdt.pojavlaunch.customcontrols.keyboard.PhysicalKeyboardInput;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.onboarding.OnboardingActivity;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferencePeripheralFragment;
import net.kdt.pojavlaunch.utils.BattlyOfflineMode;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class PeripheralInputDeviceTest {
    @Test public void keyboardRoutesAliasesAndPreservesMenuReleases() {
        int keyboard = -1;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && !device.isVirtual() && device.supportsSource(InputDevice.SOURCE_KEYBOARD)) {
                keyboard = id;
                break;
            }
        }
        assertTrue("Emulator must expose its emulated hardware keyboard", keyboard >= 0);
        Map<Integer, Integer> old = PeripheralInputSettings.keyBindings;
        Map<Integer, Integer> bindings = new HashMap<>();
        bindings.put(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_EQUALS);
        bindings.put(KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_NUMPAD_EQUALS);
        PeripheralInputSettings.keyBindings = bindings;
        ArrayList<String> sent = new ArrayList<>();
        PhysicalKeyboardInput input = new PhysicalKeyboardInput((event, key, down) -> sent.add(key + ":" + down));
        try {
            assertFalse(input.process(keyboardEvent(keyboard, KeyEvent.KEYCODE_W, false), true));
            assertFalse(input.process(keyboardEvent(keyboard, KeyEvent.KEYCODE_A, true), false));
            assertTrue(input.process(keyboardEvent(keyboard, KeyEvent.KEYCODE_A, true), true));
            assertTrue(input.process(keyboardEvent(keyboard, KeyEvent.KEYCODE_B, true), true));
            assertEquals(1, sent.size());
            assertTrue(input.process(keyboardEvent(keyboard, KeyEvent.KEYCODE_A, false), true));
            assertEquals(1, sent.size());
            input.releaseDevice(keyboard);
            assertEquals(2, sent.size());
            assertEquals(LwjglGlfwKeycode.GLFW_KEY_EQUAL + ":false", sent.get(1));
            assertFalse(input.process(keyboardEvent(keyboard, KeyEvent.KEYCODE_B, false), false));
        } finally { PeripheralInputSettings.keyBindings = old; }
    }

    private static KeyEvent keyboardEvent(int device, int key, boolean down) {
        return new KeyEvent(1, 1, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                key, 0, 0, device, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }
    @Test public void capturedPacketsPreserveEveryHistoricalDelta() {
        MotionEvent event = MotionEvent.obtain(1, 1, MotionEvent.ACTION_MOVE, 1f, 2f, 0);
        try {
            event.setSource(InputDevice.SOURCE_MOUSE_RELATIVE);
            event.addBatch(2, 3f, 4f, 1f, 1f, 0);
            float[] vector = new float[2];
            CapturedMouseMotion.read(event, vector);
            assertEquals(1, event.getHistorySize());
            assertArrayEquals(new float[]{4f, 6f}, vector, 0f);
        } finally { event.recycle(); }
    }

    @Test public void relativeAxesAreFallbackNotAnExtraCopyOfMovement() {
        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0; props.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = 5; coords.y = 2;
        coords.setAxisValue(MotionEvent.AXIS_RELATIVE_X, 5);
        coords.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, 2);
        MotionEvent event = MotionEvent.obtain(1, 1, MotionEvent.ACTION_MOVE, 1,
                new MotionEvent.PointerProperties[]{props}, new MotionEvent.PointerCoords[]{coords},
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_MOUSE_RELATIVE, 0);
        try {
            float[] vector = new float[2];
            CapturedMouseMotion.read(event, vector);
            assertArrayEquals(new float[]{5, 2}, vector, 0f);
        } finally { event.recycle(); }
        coords.x = coords.y = 0;
        event = MotionEvent.obtain(1, 1, MotionEvent.ACTION_MOVE, 1,
                new MotionEvent.PointerProperties[]{props}, new MotionEvent.PointerCoords[]{coords},
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_MOUSE_RELATIVE, 0);
        try {
            float[] vector = new float[2];
            CapturedMouseMotion.read(event, vector);
            assertArrayEquals(new float[]{5, 2}, vector, 0f);
        } finally { event.recycle(); }
    }

    @Test public void settingsPanelAndPcPresetWorkInTheLauncher() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String[] keys = {"physicalMousePcMode", "physicalMouseSensitivityX", "physicalMouseSensitivityY",
                "physicalMouseInvertX", "physicalMouseInvertY", BattlyOfflineMode.PREF_KEY,
                OnboardingActivity.getWhatsNewSeenPreferenceKey(), "battly_onboarding_completed"};
        Map<String, ?> old = prefs.getAll();
        prefs.edit().putBoolean(BattlyOfflineMode.PREF_KEY, true)
                .putBoolean("battly_onboarding_completed", true)
                .putBoolean(OnboardingActivity.getWhatsNewSeenPreferenceKey(), true).commit();
        LauncherActivity activity = null;
        try {
            activity = (LauncherActivity) instrumentation.startActivitySync(new Intent(context, LauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            LauncherActivity host = activity;
            LauncherPreferencePeripheralFragment fragment = new LauncherPreferencePeripheralFragment();
            instrumentation.runOnMainSync(() -> {
                host.getSupportFragmentManager().beginTransaction().replace(R.id.container_fragment, fragment).commitNow();
                assertNotNull(fragment.findPreference("peripheralDevices"));
                fragment.findPreference("peripheralPcPreset").performClick();
                assertTrue(prefs.getBoolean("physicalMousePcMode", false));
                assertEquals(100, prefs.getInt("physicalMouseSensitivityX", 0));
                assertEquals(100, prefs.getInt("physicalMouseSensitivityY", 0));
                assertFalse(prefs.getBoolean("physicalMouseInvertX", true));
                assertNotNull(fragment.findPreference("gamepadLinearCamera"));
            });
            instrumentation.waitForIdleSync();
            Bitmap[] rendered = new Bitmap[1];
            instrumentation.runOnMainSync(() -> {
                android.view.View view = fragment.requireView();
                assertTrue(view.getWidth() > 0 && view.getHeight() > 0);
                rendered[0] = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                view.draw(new android.graphics.Canvas(rendered[0]));
            });
            Bitmap bitmap = rendered[0];
            File screenshot = new File(context.getExternalFilesDir(null), "peripheral-settings.png");
            try (FileOutputStream stream = new FileOutputStream(screenshot)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            finally { bitmap.recycle(); }
        } finally {
            if (activity != null) { LauncherActivity host = activity; instrumentation.runOnMainSync(host::finish); }
            SharedPreferences.Editor editor = prefs.edit();
            for (String key : keys) {
                Object value = old.get(key);
                if (value == null) editor.remove(key);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            }
            editor.commit();
            instrumentation.runOnMainSync(() -> LauncherPreferences.loadPreferences(context));
        }
    }
}
