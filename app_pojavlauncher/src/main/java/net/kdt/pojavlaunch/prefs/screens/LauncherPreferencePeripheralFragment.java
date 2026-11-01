package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.PeripheralInputSettings;
import net.kdt.pojavlaunch.customcontrols.mouse.PeripheralDiagnosticView;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntConsumer;

public class LauncherPreferencePeripheralFragment extends LauncherPreferenceFragment
        implements InputManager.InputDeviceListener {
    private InputManager inputManager;
    private AlertDialog activeDialog;
    private int[] bindingCandidate;

    @Override public void onCreatePreferences(Bundle state, String rootKey) {
        addPreferencesFromResource(R.xml.pref_peripherals);
        inputManager = (InputManager) requireContext().getSystemService(Context.INPUT_SERVICE);
        for (String key : new String[]{"physicalMouseSensitivityX", "physicalMouseSensitivityY", "gamepadCameraSensitivity"}) {
            requirePreference(key, CustomSeekBarPreference.class).setSuffix(" %");
        }
        boolean captureAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        requirePreference("peripheralMouseTest").setEnabled(captureAvailable);
        requirePreference("physicalMousePcMode").setEnabled(captureAvailable);
        requirePreference("peripheralPcPreset").setEnabled(captureAvailable);
        requirePreference("peripheralPcPreset").setOnPreferenceClickListener(p -> {
            getPreferenceManager().getSharedPreferences().edit().putBoolean("physicalMousePcMode", true)
                    .putInt("physicalMouseSensitivityX", 100).putInt("physicalMouseSensitivityY", 100)
                    .putBoolean("physicalMouseInvertX", false).putBoolean("physicalMouseInvertY", false).apply();
            requirePreference("physicalMousePcMode", SwitchPreferenceCompat.class).setChecked(true);
            requirePreference("physicalMouseInvertX", SwitchPreferenceCompat.class).setChecked(false);
            requirePreference("physicalMouseInvertY", SwitchPreferenceCompat.class).setChecked(false);
            requirePreference("physicalMouseSensitivityX", CustomSeekBarPreference.class).setValue(100);
            requirePreference("physicalMouseSensitivityY", CustomSeekBarPreference.class).setValue(100);
            return true;
        });
        requirePreference("peripheralMouseTest").setOnPreferenceClickListener(p -> { showDiagnostic(true); return true; });
        requirePreference("peripheralKeyboardTest").setOnPreferenceClickListener(p -> { showDiagnostic(false); return true; });
        requirePreference("peripheralKeyboardBindings").setOnPreferenceClickListener(p -> { showBindings(); return true; });
    }

    @Override public void onResume() {
        super.onResume();
        if (inputManager != null) inputManager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        updateDevices();
    }

    @Override public void onPause() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        if (activeDialog != null) activeDialog.dismiss();
        super.onPause();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        super.onSharedPreferenceChanged(prefs, key);
        if (PeripheralInputSettings.KEY_BINDINGS.equals(key)) updateDevices();
    }

    private void updateDevices() {
        if (!isAdded()) return;
        ArrayList<String> names = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual()) continue;
            if (device.supportsSource(InputDevice.SOURCE_MOUSE) || device.supportsSource(InputDevice.SOURCE_MOUSE_RELATIVE)
                    || device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                    || device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK)) {
                names.add(device.getName());
            }
        }
        requirePreference("peripheralDevices").setSummary(names.isEmpty()
                ? getString(R.string.peripheral_no_devices) : android.text.TextUtils.join("\n", names));
        requirePreference("peripheralKeyboardBindings").setSummary(getString(
                R.string.peripheral_binding_count, PeripheralInputSettings.keyBindings.size()));
    }

    private void showDiagnostic(boolean mouse) {
        PeripheralDiagnosticView diagnostic = new PeripheralDiagnosticView(requireContext(), mouse);
        activeDialog = new AlertDialog.Builder(requireContext())
                .setTitle(mouse ? R.string.peripheral_mouse_test : R.string.peripheral_keyboard_test)
                .setView(diagnostic).setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.peripheral_reset_test, null).create();
        activeDialog.setOnShowListener(d -> {
            diagnostic.requestFocus();
            activeDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> diagnostic.reset());
        });
        activeDialog.setOnKeyListener((d, code, event) -> {
            if (code == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (mouse) diagnostic.releaseCapture(); else d.dismiss();
                }
                return true;
            }
            return code != KeyEvent.KEYCODE_BACK && diagnostic.recordKey(event);
        });
        activeDialog.setOnDismissListener(d -> diagnostic.releaseCapture());
        activeDialog.show();
    }

    private void showBindings() {
        Map<Integer, Integer> bindings = new TreeMap<>(PeripheralInputSettings.keyBindings);
        ArrayList<Integer> sources = new ArrayList<>(bindings.keySet());
        ArrayList<String> labels = new ArrayList<>();
        labels.add(getString(R.string.peripheral_add_binding));
        for (int source : sources) labels.add(keyName(source) + " -> " + keyName(bindings.get(source)));
        activeDialog = new AlertDialog.Builder(requireContext()).setTitle(R.string.peripheral_bindings)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == 0) captureKey(R.string.peripheral_source_key,
                            source -> captureKey(R.string.peripheral_target_key, target -> saveBinding(source, target)));
                    else {
                        bindings.remove(sources.get(which - 1));
                        persistBindings(bindings);
                        showBindings();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void captureKey(int title, IntConsumer result) {
        int[] pressed = {Integer.MIN_VALUE, KeyEvent.KEYCODE_UNKNOWN};
        bindingCandidate = pressed;
        activeDialog = new AlertDialog.Builder(requireContext()).setTitle(title)
                .setMessage(R.string.peripheral_press_key).setNegativeButton(android.R.string.cancel, null).create();
        // Finish on key-up so the same physical press cannot become the destination key.
        activeDialog.setOnKeyListener((dialog, code, event) -> {
            if (code == KeyEvent.KEYCODE_BACK) return false;
            if (event.getDevice() == null || event.getDevice().isVirtual()
                    || !event.isFromSource(InputDevice.SOURCE_KEYBOARD)) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && pressed[0] == Integer.MIN_VALUE) {
                pressed[0] = event.getDeviceId();
                pressed[1] = code;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (pressed[0] != event.getDeviceId() || pressed[1] != code) return true;
                pressed[0] = Integer.MIN_VALUE;
                pressed[1] = KeyEvent.KEYCODE_UNKNOWN;
                if (event.isCanceled()) return true;
                if (!PeripheralInputSettings.isBindableKey(code)) {
                    Toast.makeText(requireContext(), R.string.peripheral_invalid_key, Toast.LENGTH_SHORT).show();
                } else {
                    dialog.dismiss();
                    result.accept(code);
                }
            }
            return true;
        });
        activeDialog.setOnDismissListener(dialog -> {
            if (bindingCandidate == pressed) bindingCandidate = null;
        });
        activeDialog.show();
    }

    private void saveBinding(int source, int target) {
        Map<Integer, Integer> bindings = new TreeMap<>(PeripheralInputSettings.keyBindings);
        if (source == target) bindings.remove(source); else bindings.put(source, target);
        persistBindings(bindings);
        showBindings();
    }

    private void persistBindings(Map<Integer, Integer> bindings) {
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<Integer, Integer> entry : bindings.entrySet()) json.put(String.valueOf(entry.getKey()), entry.getValue());
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        getPreferenceManager().getSharedPreferences().edit()
                .putString(PeripheralInputSettings.KEY_BINDINGS, json.toString()).apply();
        LauncherPreferences.loadPreferences(requireContext());
        updateDevices();
    }

    private static String keyName(int key) { return KeyEvent.keyCodeToString(key).replace("KEYCODE_", ""); }
    @Override public void onInputDeviceAdded(int id) { updateDevices(); }
    @Override public void onInputDeviceRemoved(int id) {
        if (bindingCandidate != null && bindingCandidate[0] == id) {
            bindingCandidate[0] = Integer.MIN_VALUE;
            bindingCandidate[1] = KeyEvent.KEYCODE_UNKNOWN;
        }
        updateDevices();
    }
    @Override public void onInputDeviceChanged(int id) { updateDevices(); }
}
