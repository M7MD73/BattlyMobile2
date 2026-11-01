package net.kdt.pojavlaunch.customcontrols.keyboard;

import android.content.Context;
import androidx.preference.PreferenceManager;

public final class GameKeyboardPreferences {
    public static final String AUTO_OPEN = "autoOpenGameKeyboard";
    private GameKeyboardPreferences() {}

    public static boolean autoOpen(Context context) {
        return context != null && PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(AUTO_OPEN, true);
    }
}
