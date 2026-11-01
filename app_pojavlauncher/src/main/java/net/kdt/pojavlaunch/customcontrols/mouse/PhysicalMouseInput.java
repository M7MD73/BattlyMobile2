package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.InputDevice;
import android.view.MotionEvent;

public final class PhysicalMouseInput {
    private PhysicalMouseInput() {}

    public static boolean isMouseSource(int source) {
        return (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                || (source & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE;
    }

    public static boolean isMouseEvent(MotionEvent event) {
        if (isMouseSource(event.getSource())) return true;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_MOUSE) return true;
        }
        return false;
    }
}
