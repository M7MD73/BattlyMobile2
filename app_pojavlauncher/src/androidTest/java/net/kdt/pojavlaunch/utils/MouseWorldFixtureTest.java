package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

/** Explicit opt-in fixture for manual/emulator mouse tests; restores account/profile selection. */
public class MouseWorldFixtureTest {
    private static final String ID = "battly-mouse-qa";
    private static final String ACCOUNT = "BattlyMouseQA";

    @Test public void configureOrRestore() throws Exception {
        String action = InstrumentationRegistry.getArguments().getString("mouseFixture", "");
        if (action.isEmpty()) return;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences backup = context.getSharedPreferences("mouse_qa_selection", 0);
        LauncherProfiles.load();
        if ("restore".equals(action)) {
            assertTrue(backup.getBoolean("saved", false));
            PojavProfile.getPrefs(context).edit().putString("file", backup.getString("account", "")).commit();
            LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                    backup.getString("profile", "")).commit();
            LauncherProfiles.mainProfileJson.profiles.remove(ID);
            LauncherProfiles.write();
            File accountFile = new File(Tools.DIR_ACCOUNT_NEW, ACCOUNT + ".json");
            assertTrue(!accountFile.exists() || accountFile.delete());
            backup.edit().clear().commit();
            return;
        }
        assertEquals("setup", action);
        if (!backup.getBoolean("saved", false)) {
            assertFalse(LauncherProfiles.mainProfileJson.profiles.containsKey(ID));
            assertFalse(new File(Tools.DIR_ACCOUNT_NEW, ACCOUNT + ".json").exists());
            backup.edit().putBoolean("saved", true)
                    .putString("account", PojavProfile.getCurrentProfileName(context))
                    .putString("profile", LauncherPreferences.DEFAULT_PREF.getString(
                            LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "")).commit();
        }
        MinecraftAccount account = new MinecraftAccount();
        account.username = ACCOUNT;
        account.save();
        PojavProfile.getPrefs(context).edit().putString("file", ACCOUNT).commit();
        MinecraftProfile profile = new MinecraftProfile();
        profile.name = "Mouse QA (isolated)";
        profile.lastVersionId = "1.21.11";
        profile.gameDir = new File(context.getExternalFilesDir(null), "mouse-qa-world").getAbsolutePath();
        profile.pojavRendererName = "opengles2";
        LauncherProfiles.putNewProfile(ID, profile);
        LauncherProfiles.write();
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, ID).commit();
    }
}
