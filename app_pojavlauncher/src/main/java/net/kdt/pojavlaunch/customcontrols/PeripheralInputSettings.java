package net.kdt.pojavlaunch.customcontrols;

import android.content.SharedPreferences;
import android.view.KeyEvent;
import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class PeripheralInputSettings {
    public static volatile boolean pcMouse;
    public static volatile int mouseX = 100, mouseY = 100;
    public static volatile boolean invertX, invertY, linearStick;
    public static volatile Map<Integer, Integer> keyBindings = Collections.emptyMap();
    public static final String KEY_BINDINGS = "physicalKeyboardBindings";

    private PeripheralInputSettings() {}

    public static void load(SharedPreferences prefs) {
        pcMouse = prefs.getBoolean("physicalMousePcMode", false);
        mouseX = prefs.getInt("physicalMouseSensitivityX", 100);
        mouseY = prefs.getInt("physicalMouseSensitivityY", 100);
        invertX = prefs.getBoolean("physicalMouseInvertX", false);
        invertY = prefs.getBoolean("physicalMouseInvertY", false);
        linearStick = prefs.getBoolean("gamepadLinearCamera", false);
        Map<Integer, Integer> bindings = new HashMap<>();
        try {
            JSONObject json = new JSONObject(prefs.getString(KEY_BINDINGS, "{}"));
            Iterator<String> keys = json.keys();
            while (keys.hasNext() && bindings.size() < 128) {
                String key = keys.next();
                int source = Integer.parseInt(key), target = json.getInt(key);
                if (isBindableKey(source) && isBindableKey(target) && source != target) bindings.put(source, target);
            }
        } catch (Exception ignored) { bindings.clear(); }
        keyBindings = Collections.unmodifiableMap(bindings);
    }

    public static boolean isBindableKey(int key) {
        return !KeyEvent.isModifierKey(key) && key != KeyEvent.KEYCODE_BACK
                && key != KeyEvent.KEYCODE_HOME && key != KeyEvent.KEYCODE_POWER
                && key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN
                && key != KeyEvent.KEYCODE_CAPS_LOCK && key != KeyEvent.KEYCODE_NUM_LOCK
                && EfficientAndroidLWJGLKeycode.containsIndex(EfficientAndroidLWJGLKeycode.getIndexByKey(key));
    }
}
