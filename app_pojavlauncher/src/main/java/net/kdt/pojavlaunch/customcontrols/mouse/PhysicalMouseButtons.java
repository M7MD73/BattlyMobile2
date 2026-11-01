package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

/** Shared by captured motion, ordinary motion and Android's mouse key fallback. */
public final class PhysicalMouseButtons {
    public interface Sink {
        void send(int glfwButton, boolean pressed);
    }

    private final Sink sink;
    private final Map<Integer, Integer> pressedByDevice = new HashMap<>();

    public PhysicalMouseButtons(Sink sink) {
        this.sink = sink;
    }

    public static int toGlfw(int androidButton) {
        switch (androidButton) {
            case MotionEvent.BUTTON_PRIMARY: return 0;
            case MotionEvent.BUTTON_SECONDARY: return 1;
            case MotionEvent.BUTTON_TERTIARY: return 2;
            case MotionEvent.BUTTON_BACK: return 3;
            case MotionEvent.BUTTON_FORWARD: return 4;
            default: return -1;
        }
    }

    public static int fromKey(int keyCode, int scanCode) {
        if (keyCode == KeyEvent.KEYCODE_FORWARD) return MotionEvent.BUTTON_FORWARD;
        if (keyCode != KeyEvent.KEYCODE_BACK) return 0;
        // BTN_RIGHT is still reported as BACK by some Android mouse drivers.
        return scanCode == 273 ? MotionEvent.BUTTON_SECONDARY : MotionEvent.BUTTON_BACK;
    }

    public static int toSdlAndroidButton(int glfwButton) {
        switch (glfwButton) {
            case 0: return MotionEvent.BUTTON_PRIMARY;
            case 1: return MotionEvent.BUTTON_SECONDARY;
            case 2: return MotionEvent.BUTTON_TERTIARY;
            // SDL's Android TranslateButton maps FORWARD to X1 and BACK to X2.
            case 3: return MotionEvent.BUTTON_FORWARD;
            case 4: return MotionEvent.BUTTON_BACK;
            default: return 0;
        }
    }

    public synchronized boolean send(int deviceId, int androidButton, boolean pressed) {
        int button = toGlfw(androidButton);
        if (button < 0) return false;
        int mask = 1 << button;
        int oldState = pressedByDevice.containsKey(deviceId) ? pressedByDevice.get(deviceId) : 0;
        int newState = pressed ? oldState | mask : oldState & ~mask;
        if (oldState == newState) return true;
        boolean wasPressed = (combinedState() & mask) != 0;
        if (newState == 0) pressedByDevice.remove(deviceId);
        else pressedByDevice.put(deviceId, newState);
        boolean isPressed = (combinedState() & mask) != 0;
        if (wasPressed != isPressed) sink.send(button, isPressed);
        return true;
    }

    public synchronized void releaseAll() {
        int state = combinedState();
        pressedByDevice.clear();
        for (int button = 0; button < 5; button++) {
            if ((state & (1 << button)) != 0) sink.send(button, false);
        }
    }

    private int combinedState() {
        int state = 0;
        for (int value : pressedByDevice.values()) state |= value;
        return state;
    }
}
