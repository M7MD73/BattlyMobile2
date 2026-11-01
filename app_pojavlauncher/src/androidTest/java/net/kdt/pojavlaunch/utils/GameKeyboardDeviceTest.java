package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.customcontrols.keyboard.GameKeyboardPreferences;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.customcontrols.keyboard.CharacterSenderStrategy;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameKeyboardDeviceTest {
    @Test public void regainingWindowFocusDoesNotDisableEditor() {
        android.app.Instrumentation i = InstrumentationRegistry.getInstrumentation();
        Activity activity = i.startActivitySync(new Intent(i.getTargetContext(), LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        i.runOnMainSync(() -> {
            TouchCharInput input = new TouchCharInput(activity);
            StringBuilder typed = new StringBuilder();
            input.setCharacterSender(new CharacterSenderStrategy() {
                public void sendBackspace() { if (typed.length() > 0) typed.deleteCharAt(typed.length() - 1); }
                public void sendEnter() {}
                public void sendChar(char character) { typed.append(character); }
            });
            input.enable();
            input.onWindowFocusChanged(true);
            assertTrue(input.isEnabled());
            assertTrue(input.isFocusableInTouchMode());
            android.view.inputmethod.InputConnection ic = input.onCreateInputConnection(new android.view.inputmethod.EditorInfo());
            assertNotNull(ic);
            assertTrue(ic.commitText("abc", 1));
            assertEquals("abc", typed.toString());
            assertTrue(ic.deleteSurroundingText(1, 0));
            assertEquals("ab", typed.toString());
            assertTrue(ic.deleteSurroundingTextInCodePoints(1, 0));
            assertEquals("a", typed.toString());
            input.onWindowFocusChanged(false);
            assertFalse(input.isEnabled());
        });
    }

    @Test public void automaticKeyboardPreferenceDefaultsOnAndCanBeDisabled() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean existed = prefs.contains(GameKeyboardPreferences.AUTO_OPEN);
        boolean previous = prefs.getBoolean(GameKeyboardPreferences.AUTO_OPEN, true);
        try {
            prefs.edit().remove(GameKeyboardPreferences.AUTO_OPEN).commit();
            assertTrue(GameKeyboardPreferences.autoOpen(context));
            prefs.edit().putBoolean(GameKeyboardPreferences.AUTO_OPEN, false).commit();
            assertFalse(GameKeyboardPreferences.autoOpen(context));
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (existed) editor.putBoolean(GameKeyboardPreferences.AUTO_OPEN, previous);
            else editor.remove(GameKeyboardPreferences.AUTO_OPEN);
            editor.commit();
        }
    }
}
