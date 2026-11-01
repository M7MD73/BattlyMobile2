package net.kdt.pojavlaunch.customcontrols.keyboard;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SdlCharSenderTest {
    @Test
    public void sendsCommittedTextAsOneSdlTextEvent() {
        RecordingSink sink = new RecordingSink();
        SdlCharSender sender = new SdlCharSender(sink);

        sender.sendText("Hola 🌍");

        assertEquals("Hola 🌍", sink.committedText);
        assertEquals(1, sink.commitCount);
    }

    @Test
    public void sendsBackspaceAndEnterAsBalancedSdlKeys() {
        RecordingSink sink = new RecordingSink();
        SdlCharSender sender = new SdlCharSender(sink);

        sender.sendBackspace();
        sender.sendEnter();

        assertEquals(Arrays.asList(
                "down:" + KeyEvent.KEYCODE_DEL,
                "up:" + KeyEvent.KEYCODE_DEL,
                "down:" + KeyEvent.KEYCODE_ENTER,
                "up:" + KeyEvent.KEYCODE_ENTER), sink.events);
    }

    private static final class RecordingSink implements SdlCharSender.SdlInputSink {
        String committedText;
        int commitCount;
        final List<String> events = new ArrayList<>();

        @Override
        public void commitText(String text) {
            committedText = text;
            commitCount++;
        }

        @Override
        public void keyDown(int androidKeyCode) {
            events.add("down:" + androidKeyCode);
        }

        @Override
        public void keyUp(int androidKeyCode) {
            events.add("up:" + androidKeyCode);
        }
    }
}
