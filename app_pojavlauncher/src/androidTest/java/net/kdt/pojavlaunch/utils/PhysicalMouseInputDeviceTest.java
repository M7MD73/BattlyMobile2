package net.kdt.pojavlaunch.utils;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.customcontrols.gamepad.Gamepad;
import net.kdt.pojavlaunch.customcontrols.mouse.PhysicalMouseInput;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhysicalMouseInputDeviceTest {
    @Test public void gameViewCanTakeMouseFocusAfterTouchscreenLaunch() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            MinecraftGLSurface surface = new MinecraftGLSurface(activity);
            assertTrue(surface.isFocusable());
            assertTrue(surface.isFocusableInTouchMode());
        });
    }

    @Test public void mouseAndControllerEventsStaySeparate() {
        assertFalse(PhysicalMouseInput.isMouseSource(InputDevice.SOURCE_TOUCHSCREEN));
        assertFalse(PhysicalMouseInput.isMouseSource(InputDevice.SOURCE_JOYSTICK));
        assertTrue(PhysicalMouseInput.isMouseSource(InputDevice.SOURCE_MOUSE_RELATIVE));
        assertTrue(PhysicalMouseInput.isMouseSource(InputDevice.SOURCE_MOUSE));
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 5, 8, 0);
        try {
            event.setSource(InputDevice.SOURCE_JOYSTICK);
            assertTrue(Gamepad.isGamepadEvent(event));
            event.setSource(InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_JOYSTICK);
            assertTrue(PhysicalMouseInput.isMouseEvent(event));
            assertFalse(Gamepad.isGamepadEvent(event));
            event.setSource(InputDevice.SOURCE_MOUSE_RELATIVE);
            assertFalse(Gamepad.isGamepadEvent(event));
        } finally { event.recycle(); }
        KeyEvent mouseKey = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK,
                0, 0, -1, 0, 0, InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_GAMEPAD);
        assertFalse(Gamepad.isGamepadEvent(mouseKey));
    }
}
