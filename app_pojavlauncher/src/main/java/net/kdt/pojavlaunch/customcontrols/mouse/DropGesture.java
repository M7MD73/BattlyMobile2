package net.kdt.pojavlaunch.customcontrols.mouse;

import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;

import android.os.Handler;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class DropGesture implements Runnable{
    private final Handler mHandler;
    private boolean mActive;

    public DropGesture(Handler mHandler) {
        this.mHandler = mHandler;
    }

    public void submit() {
        if(isDisabled()) {
            cancel();
            return;
        }
        if(!mActive) {
            mActive = true;
            mHandler.postDelayed(this, LauncherPreferences.PREF_LONGPRESS_TRIGGER);
        }
    }

    public void cancel() {
        mActive = false;
        mHandler.removeCallbacks(this);
    }

    @Override
    public void run() {
        if(!mActive) return;
        if(isDisabled()) {
            cancel();
            return;
        }
        sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_Q);
        mHandler.postDelayed(this, 250);
    }

    private static boolean isDisabled() {
        return isDisabled(
                LauncherPreferences.PREF_DISABLE_GESTURES,
                LauncherPreferences.PREF_DISABLE_LONG_PRESS_DROP);
    }

    static boolean isDisabled(boolean allGesturesDisabled, boolean longPressDropDisabled) {
        return allGesturesDisabled || longPressDropDisabled;
    }
}
