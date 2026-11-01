package net.kdt.pojavlaunch.customcontrols.keyboard;

import android.view.KeyEvent;

import org.libsdl.app.SDLActivity;

/** Sends virtual-keyboard input through Minecraft's SDL input bridge. */
public final class SdlCharSender implements CharacterSenderStrategy {
    interface SdlInputSink {
        void commitText(String text);
        void keyDown(int androidKeyCode);
        void keyUp(int androidKeyCode);
    }

    private static final SdlInputSink NATIVE_SINK = new SdlInputSink() {
        @Override
        public void commitText(String text) {
            SDLActivity.commitTextInput(text);
        }

        @Override
        public void keyDown(int androidKeyCode) {
            SDLActivity.onNativeKeyDown(androidKeyCode);
        }

        @Override
        public void keyUp(int androidKeyCode) {
            SDLActivity.onNativeKeyUp(androidKeyCode);
        }
    };

    private final SdlInputSink sink;

    public SdlCharSender() {
        this(NATIVE_SINK);
    }

    SdlCharSender(SdlInputSink sink) {
        this.sink = sink;
    }

    @Override
    public void sendBackspace() {
        sendKey(KeyEvent.KEYCODE_DEL);
    }

    @Override
    public void sendEnter() {
        sendKey(KeyEvent.KEYCODE_ENTER);
    }

    @Override
    public void sendChar(char character) {
        sink.commitText(String.valueOf(character));
    }

    @Override
    public void sendText(CharSequence text) {
        if (text != null && text.length() > 0) {
            sink.commitText(text.toString());
        }
    }

    private void sendKey(int androidKeyCode) {
        sink.keyDown(androidKeyCode);
        sink.keyUp(androidKeyCode);
    }
}
