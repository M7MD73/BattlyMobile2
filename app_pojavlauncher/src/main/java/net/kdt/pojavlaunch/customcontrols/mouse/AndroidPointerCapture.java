package net.kdt.pojavlaunch.customcontrols.mouse;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_MOUSE_GRAB_FORCE;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.customcontrols.PeripheralInputSettings;
import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AndroidPointerCapture implements ViewTreeObserver.OnWindowFocusChangeListener, View.OnCapturedPointerListener, GrabListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final float TOUCHPAD_SCROLL_THRESHOLD = 1;
    private final AbstractTouchpad mTouchpad;
    private final View mHostView;
    private final float mMousePrescale = Tools.dpToPx(1);
    private final PointerTracker mPointerTracker = new PointerTracker();
    private final Scroller mScroller = new Scroller(TOUCHPAD_SCROLL_THRESHOLD);
    private final float[] mVector = mPointerTracker.getMotionVector();

    private int mInputDeviceIdentifier = Integer.MIN_VALUE;
    private boolean mDeviceSupportsRelativeAxis;

    public AndroidPointerCapture(AbstractTouchpad touchpad, View hostView) {
        this.mTouchpad = touchpad;
        this.mHostView = hostView;
        hostView.setOnCapturedPointerListener(this);
        hostView.getViewTreeObserver().addOnWindowFocusChangeListener(this);
        DEFAULT_PREF.registerOnSharedPreferenceChangeListener(this);
        CallbackBridge.addGrabListener(this);
    }

    /**
     * Checks whether or not the touchpad is already enabled and if user prefers virtual cursor
     * if they don't, the touchpad is not enabled
     */
    private void enableTouchpadIfNecessary() {
        if(!mTouchpad.getDisplayState() && PREF_MOUSE_GRAB_FORCE) mTouchpad.enable(true);
    }

    // Needed so it releases the cursor when inside game menu
    @Override
    public void onGrabState(boolean isGrabbing) {
        handleAutomaticCapture();
    }
    // It's only here so the side-dialog changes it live
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (sharedPreferences.getBoolean("always_grab_mouse", true)){
            enableTouchpadIfNecessary();
        } else mTouchpad.disable();
        handleAutomaticCapture();
    }

    public void handleAutomaticCapture() {
        // isGrabbing checks for whether we are in menu
        if (!CallbackBridge.isGrabbing()
        && !PREF_MOUSE_GRAB_FORCE) {
            mHostView.releasePointerCapture();
            return;
        }
        if (mHostView.hasPointerCapture()) {
            enableTouchpadIfNecessary();
        }
        if (!mHostView.hasWindowFocus()) return;
        if (!mHostView.hasFocus()) {
            mHostView.requestFocus();
        }
        if (mHostView.hasFocus()) mHostView.requestPointerCapture();
    }

    @Override
    public boolean onCapturedPointer(View view, MotionEvent event) {
        checkSameDevice(event.getDevice());
        int action = event.getActionMasked();
        boolean motion = action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE;
        // Yes, we actually not only receive relative mouse events here, but also absolute touchpad ones!
        // Therefore, we need to know when it's a touchpad and when it's a mouse.

        if (PhysicalMouseInput.isMouseEvent(event)) {
            readCapturedMouseMotion(event, mVector);
        } else if((event.getSource() & InputDevice.SOURCE_CLASS_TRACKBALL) != 0) {
            // If the source claims to be a relative device by belonging to the trackball class,
            // use its coordinates directly.
            if (event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)) {
                readCapturedMouseMotion(event, mVector);
            } else if(mDeviceSupportsRelativeAxis) {
                // If some OEM decides to do a funny and make an absolute touchpad report itself as
                // a trackball, we will at least have semi-valid relative positions
                mVector[0] = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
                mVector[1] = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
                if (mVector[0] == 0 && mVector[1] == 0) {
                    readCapturedMouseMotion(event, mVector);
                }
            }else {
                // Otherwise trust the OS, i guess??
                mVector[0] = event.getX();
                mVector[1] = event.getY();
            }
        }else {
            // If it's not a trackball, it's likely a touchpad and needs tracking like a touchscreen.
            mPointerTracker.trackEvent(event);
            // The relative position will already be written down into the mVector variable.
        }

        if (motion && !CallbackBridge.isGrabbing()) {
            enableTouchpadIfNecessary();
            // Yes, if the user's touchpad is multi-touch we will also receive events for that.
            // So, handle the scrolling gesture ourselves.
            mVector[0] *= mMousePrescale;
            mVector[1] *= mMousePrescale;
            if(event.getPointerCount() < 2) {
                mTouchpad.applyMotionVector(mVector);
                mScroller.resetScrollOvershoot();
            } else {
                mScroller.performScroll(mVector);
            }
        } else if (motion) {
            CallbackBridge.sendCursorDelta(
                    PeripheralMotion.mouseAxis(mVector[0], PeripheralInputSettings.mouseX,
                            PeripheralInputSettings.invertX, PeripheralInputSettings.pcMouse, LauncherPreferences.PREF_SCALE_FACTOR),
                    PeripheralMotion.mouseAxis(mVector[1], PeripheralInputSettings.mouseY,
                            PeripheralInputSettings.invertY, PeripheralInputSettings.pcMouse, LauncherPreferences.PREF_SCALE_FACTOR));
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                return MinecraftGLSurface.sendMouseButtonUnconverted(event.getDeviceId(), event.getActionButton(), true);
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return MinecraftGLSurface.sendMouseButtonUnconverted(event.getDeviceId(), event.getActionButton(), false);
            case MotionEvent.ACTION_SCROLL:
                CallbackBridge.sendScroll(
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                );
                return true;
            case MotionEvent.ACTION_UP:
                mPointerTracker.cancelTracking();
                return true;
            default:
                return false;
        }
    }

    static void readCapturedMouseMotion(MotionEvent event, float[] vector) {
        CapturedMouseMotion.read(event, vector);
    }

    private void checkSameDevice(InputDevice inputDevice) {
        if (inputDevice == null) {
            mInputDeviceIdentifier = Integer.MIN_VALUE;
            mDeviceSupportsRelativeAxis = false;
            return;
        }
        int newIdentifier = inputDevice.getId();
        if(mInputDeviceIdentifier != newIdentifier) {
            reinitializeDeviceSpecificProperties(inputDevice);
            mInputDeviceIdentifier = newIdentifier;
        }
    }

    private void reinitializeDeviceSpecificProperties(InputDevice inputDevice) {
        mPointerTracker.cancelTracking();
        boolean relativeXSupported = inputDevice.getMotionRange(MotionEvent.AXIS_RELATIVE_X) != null;
        boolean relativeYSupported = inputDevice.getMotionRange(MotionEvent.AXIS_RELATIVE_Y) != null;
        mDeviceSupportsRelativeAxis = relativeXSupported && relativeYSupported;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!CallbackBridge.isGrabbing() // Only capture if not in menu and user said so
        && !PREF_MOUSE_GRAB_FORCE) {
            return;
        }
        if (hasFocus && Tools.isAndroid8OrHigher()) handleAutomaticCapture();
    }

    public void detach() {
        DEFAULT_PREF.unregisterOnSharedPreferenceChangeListener(this);
        CallbackBridge.removeGrabListener(this);
        mHostView.setOnCapturedPointerListener(null);
        mHostView.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
    }
}
