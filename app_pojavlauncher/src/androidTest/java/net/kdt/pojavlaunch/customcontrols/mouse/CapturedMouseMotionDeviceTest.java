package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.InputDevice;
import android.view.MotionEvent;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class CapturedMouseMotionDeviceTest {
    @Test public void capturedMouseUsesAndroidCoordinatesAndSupportsRelativeOnlyDevices() {
        checkMotion(12, -7, 0, 0, 12, -7);
        checkMotion(12, -7, 3, 4, 12, -7);
        checkMotion(0, 0, 3, -4, 3, -4);
        checkMotion(0, 0, 0, 0, 0, 0);
    }

    private void checkMotion(float x, float y, float relativeX, float relativeY,
                             float expectedX, float expectedY) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.setAxisValue(MotionEvent.AXIS_RELATIVE_X, relativeX);
        coords.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relativeY);
        MotionEvent event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, 1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords}, 0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_MOUSE_RELATIVE, 0);
        try {
            float[] vector = new float[2];
            AndroidPointerCapture.readCapturedMouseMotion(event, vector);
            assertArrayEquals(new float[]{expectedX, expectedY}, vector, 0);
        } finally {
            event.recycle();
        }
    }
}
