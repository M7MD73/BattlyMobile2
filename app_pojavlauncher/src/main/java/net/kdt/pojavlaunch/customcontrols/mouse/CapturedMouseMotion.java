package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.MotionEvent;

public final class CapturedMouseMotion {
    private CapturedMouseMotion() {}

    public static void read(MotionEvent event, float[] vector) {
        float x = 0, y = 0;
        // Captured coordinates are deltas, not absolute positions. Every batched sample counts.
        for (int sample = 0; sample <= event.getHistorySize(); sample++) {
            boolean current = sample == event.getHistorySize();
            float dx = current ? event.getX() : event.getHistoricalX(sample);
            float dy = current ? event.getY() : event.getHistoricalY(sample);
            if (dx == 0 && dy == 0) {
                dx = current ? event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                        : event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, sample);
                dy = current ? event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                        : event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, sample);
            }
            if (!Float.isNaN(dx) && !Float.isInfinite(dx)) x += dx;
            if (!Float.isNaN(dy) && !Float.isInfinite(dy)) y += dy;
        }
        vector[0] = x;
        vector[1] = y;
    }
}
