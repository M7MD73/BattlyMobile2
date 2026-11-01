package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.customcontrols.mouse.GameCursorManager;

import java.io.IOException;

public class LauncherPreferenceControlFragment extends LauncherPreferenceFragment {
    private boolean mGyroAvailable = false;
    private final ActivityResultLauncher<String[]> mCursorImagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onCursorImagePicked);

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        // Get values
        int longPressTrigger = LauncherPreferences.PREF_LONGPRESS_TRIGGER;
        int prefButtonSize = (int) LauncherPreferences.PREF_BUTTONSIZE;
        int mouseScale = (int) (LauncherPreferences.PREF_MOUSESCALE * 100);
        int gyroSampleRate = LauncherPreferences.PREF_GYRO_SAMPLE_RATE;
        int touchControllerVibrateLength = LauncherPreferences.PREF_TOUCHCONTROLLER_VIBRATE_LENGTH;
        float mouseSpeed = LauncherPreferences.PREF_MOUSESPEED;
        float touchscreenSensitivity = LauncherPreferences.PREF_TOUCHSCREEN_SENSITIVITY;
        float gyroSpeed = LauncherPreferences.PREF_GYRO_SENSITIVITY;
        float joystickDeadzone = LauncherPreferences.PREF_DEADZONE_SCALE;


        //Triggers a write for some reason which resets the value
        addPreferencesFromResource(R.xml.pref_control);

        CustomSeekBarPreference seek2 = requirePreference("timeLongPressTrigger",
                CustomSeekBarPreference.class);
        seek2.setValue(longPressTrigger);
        seek2.setSuffix(" ms");

        CustomSeekBarPreference seek3 = requirePreference("buttonscale",
                CustomSeekBarPreference.class);
        seek3.setValue(prefButtonSize);
        seek3.setSuffix(" %");

        CustomSeekBarPreference seek4 = requirePreference("mousescale",
                CustomSeekBarPreference.class);
        seek4.setValue(mouseScale);
        seek4.setSuffix(" %");

        CustomSeekBarPreference cursorOpacity = requirePreference("cursorOpacity",
                CustomSeekBarPreference.class);
        cursorOpacity.setValue(LauncherPreferences.PREF_CURSOR_OPACITY);
        cursorOpacity.setSuffix(" %");

        ListPreference cursorStyle = requirePreference("cursorStyle", ListPreference.class);
        cursorStyle.setValue(LauncherPreferences.PREF_CURSOR_STYLE);
        cursorStyle.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        ListPreference cursorHotspot = requirePreference("cursorHotspot", ListPreference.class);
        cursorHotspot.setValue(LauncherPreferences.PREF_CURSOR_HOTSPOT);
        cursorHotspot.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        requirePreference("cursorCustomImage").setOnPreferenceClickListener(preference -> {
            mCursorImagePicker.launch(new String[]{"image/png", "image/webp", "image/*"});
            return true;
        });

        CustomSeekBarPreference seek6 = requirePreference("mousespeed",
                CustomSeekBarPreference.class);
        seek6.setValue((int) (mouseSpeed * 100f));
        seek6.setSuffix(" %");

        CustomSeekBarPreference touchscreenSensitivitySeek = requirePreference(
                "touchscreenSensitivity", CustomSeekBarPreference.class);
        touchscreenSensitivitySeek.setValue((int) (touchscreenSensitivity * 100f));
        touchscreenSensitivitySeek.setSuffix(" %");

        CustomSeekBarPreference deadzoneSeek = requirePreference("gamepad_deadzone_scale",
                CustomSeekBarPreference.class);
        deadzoneSeek.setValue((int) (joystickDeadzone * 100f));
        deadzoneSeek.setSuffix(" %");


        Context context = getContext();
        if (context != null) {
            mGyroAvailable = Tools.deviceSupportsGyro(context);
        }
        PreferenceCategory gyroCategory = requirePreference("gyroCategory",
                PreferenceCategory.class);
        gyroCategory.setVisible(mGyroAvailable);

        CustomSeekBarPreference gyroSensitivitySeek = requirePreference("gyroSensitivity",
                CustomSeekBarPreference.class);
        gyroSensitivitySeek.setValue((int) (gyroSpeed * 100f));
        gyroSensitivitySeek.setSuffix(" %");

        CustomSeekBarPreference gyroSampleRateSeek = requirePreference("gyroSampleRate",
                CustomSeekBarPreference.class);
        gyroSampleRateSeek.setValue(gyroSampleRate);
        gyroSampleRateSeek.setSuffix(" ms");

        CustomSeekBarPreference touchControllerVibrateLengthSeek = requirePreference(
                "touchControllerVibrateLength",
                CustomSeekBarPreference.class);
        touchControllerVibrateLengthSeek.setValue(touchControllerVibrateLength);
        touchControllerVibrateLengthSeek.setSuffix(" ms");

        computeVisibility();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility() {
        requirePreference("timeLongPressTrigger").setVisible(!LauncherPreferences.PREF_DISABLE_GESTURES);
        requirePreference("gyroSensitivity").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroSampleRate").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroInvertX").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroInvertY").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroSmoothing").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        Preference customCursor = requirePreference("cursorCustomImage");
        customCursor.setVisible(GameCursorManager.STYLE_CUSTOM.equals(
                GameCursorManager.normalizeStyle(LauncherPreferences.PREF_CURSOR_STYLE)));
        customCursor.setSummary(LauncherPreferences.PREF_CURSOR_CUSTOM_PATH == null
                        || LauncherPreferences.PREF_CURSOR_CUSTOM_PATH.isEmpty()
                ? R.string.cursor_custom_image_summary_empty
                : R.string.cursor_custom_image_summary_selected);
    }

    private void onCursorImagePicked(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            String path = GameCursorManager.importCustomCursor(requireContext(), uri);
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString("cursorStyle", GameCursorManager.STYLE_CUSTOM)
                    .putString("cursorCustomPath", path)
                    .apply();
            LauncherPreferences.loadPreferences(requireContext());
            ListPreference cursorStyle = requirePreference("cursorStyle", ListPreference.class);
            cursorStyle.setValue(GameCursorManager.STYLE_CUSTOM);
            computeVisibility();
            Toast.makeText(requireContext(), R.string.cursor_custom_image_saved,
                    Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            Tools.showError(requireContext(), exception);
        }
    }

}
