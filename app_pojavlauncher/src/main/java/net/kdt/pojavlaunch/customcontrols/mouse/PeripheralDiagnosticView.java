package net.kdt.pojavlaunch.customcontrols.mouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.PeripheralInputSettings;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Locale;

/** Local diagnostic only. No typed text or input samples are persisted or uploaded. */
public class PeripheralDiagnosticView extends LinearLayout {
    private final boolean mouse;
    private final TextView status;
    private final View target;
    private final float[] delta = new float[2];
    private final Map<Long, String> held = new LinkedHashMap<>();
    private String lastKey = "";
    private int lastScan, lastRepeat;
    private final InputManager.InputDeviceListener devices = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) {}
        @Override public void onInputDeviceChanged(int id) {}
        @Override public void onInputDeviceRemoved(int id) {
            Iterator<Long> keys = held.keySet().iterator();
            while (keys.hasNext()) if ((int) (keys.next() >> 32) == id) keys.remove();
            if (!mouse) updateKeyboardStatus();
        }
    };
    private double rawX, rawY, outputX, outputY;
    private int samples;
    private String device = "";
    private boolean updatePending;

    public PeripheralDiagnosticView(Context context, boolean mouse) {
        super(context);
        this.mouse = mouse;
        setOrientation(VERTICAL);
        int padding = dp(16);
        setPadding(padding, padding, padding, padding);
        setFocusable(true);
        setFocusableInTouchMode(true);
        status = new TextView(context);
        status.setTextColor(0xffeeeeee);
        status.setTextSize(14);
        addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        target = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                paint.setColor(0xff8be0cc);
                paint.setStrokeWidth(dp(2));
                float cx = getWidth() / 2f, cy = getHeight() / 2f;
                canvas.drawLine(cx - dp(8), cy, cx + dp(8), cy, paint);
                canvas.drawLine(cx, cy - dp(8), cx, cy + dp(8), paint);
                float x = cx + (float) outputX, y = cy + (float) outputY;
                canvas.drawCircle(Math.max(dp(5), Math.min(getWidth() - dp(5), x)),
                        Math.max(dp(5), Math.min(getHeight() - dp(5), y)), dp(4), paint);
            }
        };
        target.setBackgroundColor(0xff17212b);
        target.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(target, new LayoutParams(LayoutParams.MATCH_PARENT, dp(88)));
        target.setVisibility(mouse ? VISIBLE : GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setOnCapturedPointerListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                        || event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
                    CapturedMouseMotion.read(event, delta);
                    rawX += delta[0]; rawY += delta[1];
                    outputX += PeripheralMotion.mouseAxis(delta[0], PeripheralInputSettings.mouseX,
                            PeripheralInputSettings.invertX, PeripheralInputSettings.pcMouse, LauncherPreferences.PREF_SCALE_FACTOR);
                    outputY += PeripheralMotion.mouseAxis(delta[1], PeripheralInputSettings.mouseY,
                            PeripheralInputSettings.invertY, PeripheralInputSettings.pcMouse, LauncherPreferences.PREF_SCALE_FACTOR);
                    samples += event.getHistorySize() + 1;
                    device = event.getDevice() == null ? "" : event.getDevice().getName();
                    queueUpdate();
                }
                return true;
            });
        }
        reset();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!mouse || !PhysicalMouseInput.isMouseEvent(event)) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestFocus();
            requestPointerCapture();
        }
        return true;
    }

    public boolean recordKey(KeyEvent event) {
        if (mouse || !event.isFromSource(InputDevice.SOURCE_KEYBOARD)) return false;
        String key = KeyEvent.keyCodeToString(event.getKeyCode()).replace("KEYCODE_", "");
        long id = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
        if (event.getAction() == KeyEvent.ACTION_DOWN) held.put(id, key);
        if (event.getAction() == KeyEvent.ACTION_UP) held.remove(id);
        device = event.getDevice() == null ? "" : event.getDevice().getName();
        lastKey = key; lastScan = event.getScanCode(); lastRepeat = event.getRepeatCount();
        updateKeyboardStatus();
        return true;
    }

    private void updateKeyboardStatus() {
        status.setText(getContext().getString(R.string.peripheral_key_result, device, lastKey,
                lastScan, lastRepeat, android.text.TextUtils.join(" + ", held.values())));
    }

    public void reset() {
        rawX = rawY = outputX = outputY = 0;
        samples = 0;
        held.clear();
        status.setText(mouse ? R.string.peripheral_mouse_ready : R.string.peripheral_keyboard_ready);
        target.invalidate();
    }

    private void queueUpdate() {
        if (updatePending) return;
        updatePending = true;
        postOnAnimation(() -> {
            updatePending = false;
            status.setText(getContext().getString(R.string.peripheral_mouse_result, device,
                    String.format(Locale.getDefault(), "%.2f / %.2f", rawX, rawY),
                    String.format(Locale.getDefault(), "%.2f / %.2f", outputX, outputY), samples));
            target.invalidate();
        });
    }

    @Override public void onPointerCaptureChange(boolean capture) {
        super.onPointerCaptureChange(capture);
        if (!capture && mouse) status.setText(R.string.peripheral_mouse_ready);
    }

    public void releaseCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) releasePointerCapture();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        InputManager manager = (InputManager) getContext().getSystemService(Context.INPUT_SERVICE);
        if (manager != null) manager.registerInputDeviceListener(devices, new Handler(Looper.getMainLooper()));
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus) {
            held.clear();
            if (!mouse) status.setText(R.string.peripheral_keyboard_ready);
        }
    }

    @Override protected void onDetachedFromWindow() {
        InputManager manager = (InputManager) getContext().getSystemService(Context.INPUT_SERVICE);
        if (manager != null) manager.unregisterInputDeviceListener(devices);
        held.clear();
        releaseCapture();
        super.onDetachedFromWindow();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
