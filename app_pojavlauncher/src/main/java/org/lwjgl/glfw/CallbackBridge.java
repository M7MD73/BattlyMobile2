package org.lwjgl.glfw;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.customcontrols.gamepad.direct.DirectGamepadEnableHandler;

import android.content.*;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDL;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dalvik.annotation.optimization.CriticalNative;

public class CallbackBridge {
    public static final Choreographer sChoreographer = Choreographer.getInstance();
    private static final Handler sInputHandler = new Handler(Looper.getMainLooper());
    private static boolean isGrabbing = false;
    private static final ArrayList<GrabListener> grabListeners = new ArrayList<>();
    // Use a weak reference here to avoid possibly statically referencing a Context.
    private static @Nullable WeakReference<DirectGamepadEnableHandler> sDirectGamepadEnableHandler;
    private static boolean sSdlInputBridgeLogged;
    private static int sSdlMouseButtonState;
    private static final AtomicInteger sNextAudioCaptureHandle = new AtomicInteger(1);
    private static final Map<Integer, AudioRecord> sAudioCaptures = new ConcurrentHashMap<>();
    private static volatile @Nullable Context sAndroidAudioContext;
    
    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;
    public static final int CLIPBOARD_OPEN = 2002;
    public static final int TEXT_INPUT_SHOW = 2003;
    public static final int TEXT_INPUT_HIDE = 2004;
    
    public static volatile int windowWidth, windowHeight;
    public static volatile int physicalWidth, physicalHeight;
    public static float mouseX, mouseY;
    public volatile static boolean holdingAlt, holdingCapslock, holdingCtrl,
            holdingNumlock, holdingShift;

    public static final ByteBuffer sGamepadButtonBuffer;
    public static final FloatBuffer sGamepadAxisBuffer;
    public static boolean sGamepadDirectInput = false;

    public static void putMouseEventWithCoords(int button, float x, float y) {
        putMouseEventWithCoords(button, true, x, y);
        sInputHandler.postDelayed(() -> putMouseEventWithCoords(button, false, x, y), 33);
    }
    
    public static void putMouseEventWithCoords(int button, boolean isDown, float x, float y /* , int dz, long nanos */) {
        sendCursorPos(x, y);
        sendMouseKeycode(button, CallbackBridge.getCurrentMods(), isDown);
    }


    public static void sendCursorPos(float x, float y) {
        mouseX = x;
        mouseY = y;
        if (MinecraftGLSurface.isSdlInputBridgeEnabled()) {
            sendSdlMouse(getSdlMouseButtonState(), MotionEvent.ACTION_MOVE, mouseX, mouseY);
        } else {
            nativeSendCursorPos(mouseX, mouseY);
        }
    }

    public static void sendCursorDelta(float deltaX, float deltaY) {
        if (MinecraftGLSurface.isSdlInputBridgeEnabled()) {
            sendSdlMouse(getSdlMouseButtonState(), MotionEvent.ACTION_MOVE,
                    deltaX, deltaY, true);
        } else {
            mouseX += deltaX;
            mouseY += deltaY;
            nativeSendCursorPos(mouseX, mouseY);
        }
    }

    /**
     * Sends keycodes if keycode is populated. Used for in-game controls.
     * Sends character if keychar is populated. Used for chat and text input.
     * You can refer to glfwSetKeyCallback for the arguments.
     * @param keycode LwjglGlfwKeycode
     * @param keychar Literal char. Modifier keys does not affect this.
     * @param scancode
     * @param modifiers The action is one of The action is one of GLFW_PRESS, or GLFW_RELEASE.
     *                  We don't have GLFW_REPEAT working.
     * @param isDown If its being pressed down or not. 1 is true.
     */
    public static void sendKeycode(int keycode, char keychar, int scancode, int modifiers, boolean isDown) {
        if (keycode != 0 && MinecraftGLSurface.isSdlInputBridgeEnabled()) {
            int androidKeycode = EfficientAndroidLWJGLKeycode.getAndroidKeycode(keycode);
            if (androidKeycode != KeyEvent.KEYCODE_UNKNOWN) {
                logSdlInputBridge();
                if (isDown) SDLActivity.onNativeKeyDown(androidKeycode);
                else SDLActivity.onNativeKeyUp(androidKeycode);
            } else {
                Log.w("BattlyInput", "No Android mapping for GLFW keycode " + keycode);
            }
        } else if (keycode != 0) {
            // The 3.3.3/3.4.1 bridge consumes GLFW callbacks directly. Sending the
            // same key through SDL as well produces duplicate presses in modern menus.
            nativeSendKey(keycode, scancode, isDown ? 1 : 0, modifiers);
        }
        // Only controlmaps goes through here, that means we need to block ISOControl or else
        // Minecraft tries to type :TAB: as a character in chat, fails, and then ignores the key,
        // breaking the tab autofill function in old versions. (like 1.12.2, 1.8.9).
        if(isDown && !Character.isISOControl(keychar)) {
            if (MinecraftGLSurface.isSdlInputBridgeEnabled()
                    && SDLActivity.isSDLTextInputActive()) {
                SDLActivity.onNativeTextInput(String.valueOf(keychar));
            } else {
                nativeSendCharMods(keychar,modifiers);
                nativeSendChar(keychar);
            }
        }
    }

    public static void sendChar(char keychar, int modifiers){
        // Only an EditText goes through here, that means emojis are allowed, so no isISOControl
        // cause we might break emoji mods then.
        // See net/kdt/pojavlaunch/customcontrols/keyboard/TouchCharInput.java#L147 (onTextChanged)
        nativeSendCharMods(keychar,modifiers);
        nativeSendChar(keychar);
    }

    public static void sendKeyPress(int keyCode, int modifiers, boolean status) {
        sendKeyPress(keyCode, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, int scancode, int modifiers, boolean status) {
        sendKeyPress(keyCode, '\u0000', scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int scancode, int modifiers, boolean status) {
        CallbackBridge.sendKeycode(keyCode, keyChar, scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int modifiers, boolean status) {
        sendKeyPress(keyCode, keyChar, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode) {
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendMouseButton(int button, boolean status) {
        CallbackBridge.sendMouseKeycode(button, CallbackBridge.getCurrentMods(), status);
    }

    public static void sendMouseKeycode(int button, int modifiers, boolean isDown) {
        // if (isGrabbing()) DEBUG_STRING.append("MouseGrabStrace: " + android.util.Log.getStackTraceString(new Throwable()) + "\n");
        if (MinecraftGLSurface.isSdlInputBridgeEnabled()) {
            int buttonState = updateSdlMouseButtonState(toAndroidMouseButton(button), isDown);
            sendSdlMouse(buttonState,
                    isDown ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, mouseX, mouseY);
        } else {
            nativeSendMouseButton(button, isDown ? 1 : 0, modifiers);
        }
    }

    public static void sendMouseKeycode(int keycode) {
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), true);
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), false);
    }
    
    public static void sendScroll(double xoffset, double yoffset) {
        if (MinecraftGLSurface.isSdlInputBridgeEnabled()) {
            sendSdlMouse(getSdlMouseButtonState(), MotionEvent.ACTION_SCROLL,
                    (float) xoffset, (float) yoffset);
        } else {
            nativeSendScroll(xoffset, yoffset);
        }
    }

    private static int toAndroidMouseButton(int glfwButton) {
        return net.kdt.pojavlaunch.customcontrols.mouse.PhysicalMouseButtons.toSdlAndroidButton(glfwButton);
    }

    private static void sendSdlMouse(int button, int action, float x, float y) {
        sendSdlMouse(button, action, x, y, false);
    }

    private static void sendSdlMouse(int button, int action, float x, float y,
                                     boolean relative) {
        if (!MinecraftGLSurface.isSdlInputBridgeEnabled()) return;
        try {
            logSdlInputBridge();
            SDLActivity.onNativeMouse(button, action, x, y, relative);
        } catch (UnsatisfiedLinkError error) {
            Log.w("BattlyInput", "SDL input bridge is unavailable; retaining GLFW input", error);
        }
    }

    private static void logSdlInputBridge() {
        if (sSdlInputBridgeLogged) return;
        sSdlInputBridgeLogged = true;
        Log.i("BattlyInput", "Overlay controls are routed to the SDL window");
    }

    private static synchronized int updateSdlMouseButtonState(int button, boolean isDown) {
        if (button == 0) return sSdlMouseButtonState;
        if (isDown) sSdlMouseButtonState |= button;
        else sSdlMouseButtonState &= ~button;
        return sSdlMouseButtonState;
    }

    private static synchronized int getSdlMouseButtonState() {
        return sSdlMouseButtonState;
    }

    public static void resetSdlInputState() {
        int previousState;
        synchronized (CallbackBridge.class) {
            previousState = sSdlMouseButtonState;
            sSdlMouseButtonState = 0;
        }
        if (previousState != 0 && MinecraftGLSurface.isSdlInputBridgeEnabled()) {
            sendSdlMouse(0, MotionEvent.ACTION_UP, mouseX, mouseY);
        }
    }

    public static void sendUpdateWindowSize(int w, int h) {
        nativeSendScreenSize(w, h);
    }

    public static boolean isGrabbing() {
        // Avoid going through the JNI each time.
        return isGrabbing;
    }

    // Called from JRE side
    @SuppressWarnings("unused")
    @Keep
    public static @Nullable String accessAndroidClipboard(int type, String copy) {
        switch (type) {
            case CLIPBOARD_COPY:
                MainActivity.GLOBAL_CLIPBOARD.setPrimaryClip(ClipData.newPlainText("Copy", copy));
                return null;

            case CLIPBOARD_PASTE:
                if (MainActivity.GLOBAL_CLIPBOARD.hasPrimaryClip() && MainActivity.GLOBAL_CLIPBOARD.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    return MainActivity.GLOBAL_CLIPBOARD.getPrimaryClip().getItemAt(0).getText().toString();
                } else {
                    return "";
                }

            case CLIPBOARD_OPEN:
                MainActivity.openLink(copy);
                return null;
            case TEXT_INPUT_SHOW:
                MainActivity.setGameTextInputActive(true);
                return null;
            case TEXT_INPUT_HIDE:
                MainActivity.setGameTextInputActive(false);
                return null;
            default: return null;
        }
    }

    @SuppressWarnings("unused")
    @Keep
    public static String listAndroidAudioInputs() {
        return listAndroidAudioInputs(resolveAndroidAudioContext());
    }

    public static void setAndroidAudioContext(Context context) {
        sAndroidAudioContext = context.getApplicationContext();
    }

    public static String listAndroidAudioInputs(@Nullable Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "";
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return "";

        StringBuilder result = new StringBuilder();
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (!device.isSource() || !isSelectableAudioInputType(device.getType())) continue;
            String typeName = audioInputTypeName(device.getType());
            String product = device.getProductName() == null
                    ? "" : device.getProductName().toString().trim();
            String name = product.isEmpty() || product.equalsIgnoreCase(typeName)
                    ? typeName : typeName + " - " + product;
            if (result.length() > 0) result.append('\n');
            result.append(device.getId()).append('\t')
                    .append(device.getType()).append('\t')
                    .append(name.replace('\t', ' ').replace('\n', ' '));
        }
        return result.toString();
    }

    private static boolean isSelectableAudioInputType(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return true;
            default:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && type == AudioDeviceInfo.TYPE_BLE_HEADSET;
        }
    }

    @SuppressWarnings("unused")
    @Keep
    public static int openAndroidAudioInput(int deviceId, int sampleRate,
                                            int channels, int requestedBufferSize) {
        Context context = resolveAndroidAudioContext();
        if (context == null) {
            Log.w("BattlyAudio", "Microphone capture requested without an Android context");
            return -1;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("BattlyAudio", "Microphone capture requested without RECORD_AUDIO permission");
            return -1;
        }

        int channelMask = channels == 2
                ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int minimum = AudioRecord.getMinBufferSize(sampleRate, channelMask,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) return -1;
        int bufferSize = Math.max(minimum * 2, requestedBufferSize);
        AudioRecord record = null;
        try {
            record = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelMask)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                return -1;
            }
            if (deviceId >= 0) {
                AudioDeviceInfo selected = findAudioInput(context, deviceId);
                if (selected == null || !record.setPreferredDevice(selected)) {
                    Log.w("BattlyAudio", "Unable to select Android input device " + deviceId);
                    record.release();
                    return -1;
                }
            }
            int handle = sNextAudioCaptureHandle.getAndIncrement();
            sAudioCaptures.put(handle, record);
            Log.i("BattlyAudio", "Opened Android input " + deviceId + " as capture " + handle);
            return handle;
        } catch (RuntimeException error) {
            if (record != null) record.release();
            Log.e("BattlyAudio", "Unable to open Android microphone", error);
            return -1;
        }
    }

    @SuppressWarnings("unused")
    @Keep
    public static boolean startAndroidAudioInput(int handle) {
        AudioRecord record = sAudioCaptures.get(handle);
        if (record == null) return false;
        try {
            record.startRecording();
            return record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
        } catch (RuntimeException error) {
            Log.e("BattlyAudio", "Unable to start Android microphone", error);
            return false;
        }
    }

    @SuppressWarnings("unused")
    @Keep
    public static byte[] readAndroidAudioInput(int handle, int length) {
        AudioRecord record = sAudioCaptures.get(handle);
        if (record == null || length <= 0) return new byte[0];
        byte[] data = new byte[length];
        int read = record.read(data, 0, length, AudioRecord.READ_BLOCKING);
        if (read <= 0) return new byte[0];
        if (read == length) return data;
        byte[] exact = new byte[read];
        System.arraycopy(data, 0, exact, 0, read);
        return exact;
    }

    @SuppressWarnings("unused")
    @Keep
    public static void stopAndroidAudioInput(int handle) {
        AudioRecord record = sAudioCaptures.get(handle);
        if (record == null || record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) return;
        try {
            record.stop();
        } catch (RuntimeException error) {
            Log.w("BattlyAudio", "Unable to stop Android microphone cleanly", error);
        }
    }

    @SuppressWarnings("unused")
    @Keep
    public static void closeAndroidAudioInput(int handle) {
        AudioRecord record = sAudioCaptures.remove(handle);
        if (record == null) return;
        try {
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop();
        } catch (RuntimeException ignored) {
        }
        record.release();
    }

    private static @Nullable AudioDeviceInfo findAudioInput(Context context, int deviceId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return null;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (device.isSource() && device.getId() == deviceId) return device;
        }
        return null;
    }

    private static @Nullable Context resolveAndroidAudioContext() {
        Context sdlContext = SDL.getContext();
        return sdlContext != null ? sdlContext : sAndroidAudioContext;
    }

    private static String audioInputTypeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "Internal microphone";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired headset microphone";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth microphone";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB microphone";
            default: return "Android microphone";
        }
    }


    public static int getCurrentMods() {
        int currMods = 0;
        if (holdingAlt) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_ALT;
        } if (holdingCapslock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CAPS_LOCK;
        } if (holdingCtrl) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CONTROL;
        } if (holdingNumlock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_NUM_LOCK;
        } if (holdingShift) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_SHIFT;
        }
        return currMods;
    }

    public static void setModifiers(int keyCode, boolean isDown){
        switch (keyCode){
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT:
                CallbackBridge.holdingShift = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL:
                CallbackBridge.holdingCtrl = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT:
                CallbackBridge.holdingAlt = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK:
                CallbackBridge.holdingCapslock = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK:
                CallbackBridge.holdingNumlock = isDown;
        }
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    @Keep
    private static void onDirectInputEnable() {
        Log.i("CallbackBridge", "onDirectInputEnable()");
        DirectGamepadEnableHandler enableHandler = Tools.getWeakReference(sDirectGamepadEnableHandler);
        if(enableHandler != null) enableHandler.onDirectGamepadEnabled();
        sGamepadDirectInput = true;
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    @Keep
    private static void onGrabStateChanged(final boolean grabbing) {
        setGrabState(grabbing);
    }

    /**
     * SDL-backed LWJGL versions bypass the GLFW shim when Minecraft changes
     * relative mouse mode. Mirror SDL's state into the existing grab listeners
     * so touchscreen input switches between GUI and camera handling.
     */
    public static void setSdlGrabState(final boolean grabbing) {
        setGrabState(grabbing);
    }

    private static void setGrabState(final boolean grabbing) {
        if (isGrabbing != grabbing) {
            resetSdlInputState();
        }
        isGrabbing = grabbing;
        sChoreographer.postFrameCallbackDelayed((time) -> {
            // If the grab re-changed, skip notify process
            if(isGrabbing != grabbing) return;

            Log.i("BattlyInput", "Grab changed: " + grabbing
                    + (MinecraftGLSurface.isSdlWindowBridgeEnabled() ? " (SDL)" : " (GLFW)"));
            synchronized (grabListeners) {
                for (GrabListener g : grabListeners) g.onGrabState(grabbing);
            }

        }, 16);

    }
    public static void addGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            listener.onGrabState(isGrabbing);
            grabListeners.add(listener);
        }
    }
    public static void removeGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            grabListeners.remove(listener);
        }
    }

    public static FloatBuffer createGamepadAxisBuffer() {
        ByteBuffer axisByteBuffer = nativeCreateGamepadAxisBuffer();
        // NOTE: hardcoded order (also in jre_lwjgl3glfw CallbackBridge)
        return axisByteBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    }

    public static void setDirectGamepadEnableHandler(DirectGamepadEnableHandler h) {
        sDirectGamepadEnableHandler = new WeakReference<>(h);
    }

    @Keep @CriticalNative public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);

    @Keep @CriticalNative private static native boolean nativeSendChar(char codepoint);
    // GLFW: GLFWCharModsCallback deprecated, but is Minecraft still use?
    @Keep @CriticalNative private static native boolean nativeSendCharMods(char codepoint, int mods);
    @Keep @CriticalNative private static native void nativeSendKey(int key, int scancode, int action, int mods);
    // private static native void nativeSendCursorEnter(int entered);
    @Keep @CriticalNative private static native void nativeSendCursorPos(float x, float y);
    @Keep @CriticalNative private static native void nativeSendMouseButton(int button, int action, int mods);
    @Keep @CriticalNative private static native void nativeSendScroll(double xoffset, double yoffset);
    @Keep @CriticalNative private static native void nativeSendScreenSize(int width, int height);
    public static native void nativeSetWindowAttrib(int attrib, int value);
    private static native ByteBuffer nativeCreateGamepadButtonBuffer();
    private static native ByteBuffer nativeCreateGamepadAxisBuffer();
    static {
        System.loadLibrary("pojavexec");
        sGamepadButtonBuffer = nativeCreateGamepadButtonBuffer();
        sGamepadAxisBuffer = createGamepadAxisBuffer();
    }
}

