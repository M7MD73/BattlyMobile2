package net.kdt.pojavlaunch.customcontrols.keyboard;

import android.view.InputDevice;
import android.view.KeyEvent;
import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.customcontrols.PeripheralInputSettings;
import org.lwjgl.glfw.CallbackBridge;

public final class PhysicalKeyboardInput {
    private final HeldKeyBindings held = new HeldKeyBindings();
    public interface Sink { void send(KeyEvent event, int glfwKey, boolean down); }
    private final Sink sink;

    public PhysicalKeyboardInput() {
        this((event, target, down) -> {
            if (event == null) CallbackBridge.sendKeyPress(target, CallbackBridge.getCurrentMods(), down);
            else EfficientAndroidLWJGLKeycode.execKey(event, EfficientAndroidLWJGLKeycode.getIndexByValue(target));
        });
    }

    public PhysicalKeyboardInput(Sink sink) { this.sink = sink; }

    public boolean process(KeyEvent event, boolean grabbing) {
        int source = event.getKeyCode();
        boolean tracked = held.isHeld(event.getDeviceId(), source);
        if (event.getAction() == KeyEvent.ACTION_UP && !tracked) return false;
        if (!tracked && (!grabbing || PeripheralInputSettings.keyBindings.isEmpty()
                || !event.isFromSource(InputDevice.SOURCE_KEYBOARD)
                || event.getDevice() == null || event.getDevice().isVirtual()
                || !PeripheralInputSettings.isBindableKey(source))) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return false;
        Integer binding = PeripheralInputSettings.keyBindings.get(source);
        int mappedKey = binding == null ? source : binding;
        int outputKey = EfficientAndroidLWJGLKeycode.getValueByIndex(
                EfficientAndroidLWJGLKeycode.getIndexByKey(mappedKey));
        int target = event.getAction() == KeyEvent.ACTION_DOWN
                ? held.press(event.getDeviceId(), source, outputKey)
                : held.release(event.getDeviceId(), source);
        if (target >= 0) {
            KeyEvent mapped = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                    EfficientAndroidLWJGLKeycode.getAndroidKeycode(target), 0, event.getMetaState(),
                    event.getDeviceId(), 0, event.getFlags(), event.getSource());
            sink.send(mapped, target, event.getAction() == KeyEvent.ACTION_DOWN);
        }
        return true;
    }

    public void releaseAll() {
        held.releaseAll(target -> sink.send(null, target, false));
    }

    public void releaseDevice(int device) {
        held.releaseDevice(device, target -> sink.send(null, target, false));
    }
}
