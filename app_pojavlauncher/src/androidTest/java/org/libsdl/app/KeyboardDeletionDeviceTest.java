package org.libsdl.app;

import android.view.KeyEvent;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardDeletionDeviceTest {
    private static class RecordingConnection extends SDLInputConnection {
        int deletes;
        final List<String> commits = new ArrayList<>();
        RecordingConnection(View view) { super(view, true); }
        @Override protected void emitScancode(char c) { if (c == '\b') deletes++; }
        @Override protected void emitText(String text) { commits.add(text); }
    }

    @Test public void deletionDoesNotReplayPreviouslyCommittedText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecordingConnection ic = connection();
            ic.commitText("abc", 1);
            assertTrue(ic.deleteSurroundingText(1, 0));
            assertEquals("ab", ic.getEditable().toString());
            ic.commitText("d", 1);
            assertEquals(1, ic.deletes);
            assertEquals("d", ic.commits.get(ic.commits.size() - 1));
            assertEquals("abd", ic.getEditable().toString());
        });
    }

    @Test public void codePointDeletionAndKeyEventDeleteWork() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecordingConnection ic = connection();
            ic.commitText("ab\uD83D\uDE42", 1);
            assertTrue(ic.deleteSurroundingTextInCodePoints(1, 0));
            assertEquals("ab", ic.getEditable().toString());
            assertEquals(1, ic.deletes);
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
            assertEquals("a", ic.getEditable().toString());
            assertEquals(2, ic.deletes);
        });
    }

    @Test public void backspaceStillReachesExistingGameTextWithEmptyImeBuffer() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecordingConnection ic = connection();
            assertTrue(ic.deleteSurroundingTextInCodePoints(1, 0));
            assertEquals(1, ic.deletes);
        });
    }

    private static RecordingConnection connection() {
        return new RecordingConnection(new View(InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }
}
