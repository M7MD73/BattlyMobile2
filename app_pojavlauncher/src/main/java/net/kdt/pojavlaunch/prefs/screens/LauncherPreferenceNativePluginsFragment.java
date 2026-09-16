package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.utils.NativeLibraryPluginManager;

import java.util.List;

public class LauncherPreferenceNativePluginsFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.native_plugins_category);
        screen.addPreference(category);

        List<NativeLibraryPluginManager.Plugin> plugins =
                NativeLibraryPluginManager.discover(requireContext());
        if (plugins.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setSelectable(false);
            empty.setTitle(R.string.native_plugins_empty_title);
            empty.setSummary(R.string.native_plugins_empty_summary);
            category.addPreference(empty);
            return;
        }

        for (NativeLibraryPluginManager.Plugin plugin : plugins) {
            SwitchPreferenceCompat preference = new SwitchPreferenceCompat(requireContext());
            preference.setKey("native_plugin_" + plugin.packageName);
            preference.setPersistent(false);
            preference.setTitle(plugin.appName);
            preference.setSummary(buildSummary(plugin));
            preference.setChecked(NativeLibraryPluginManager.isEnabled(
                    requireContext(), plugin.packageName));
            preference.setOnPreferenceChangeListener((ignored, newValue) -> {
                NativeLibraryPluginManager.setEnabled(
                        requireContext(), plugin.packageName, Boolean.TRUE.equals(newValue));
                return true;
            });
            category.addPreference(preference);
        }
    }

    private String buildSummary(NativeLibraryPluginManager.Plugin plugin) {
        StringBuilder summary = new StringBuilder();
        if (!plugin.description.isEmpty()) summary.append(plugin.description);
        if (!plugin.versionName.isEmpty()) appendPart(summary,
                getString(R.string.native_plugins_version, plugin.versionName));
        if (plugin.minimumMinecraftVersion != null || plugin.maximumMinecraftVersion != null) {
            String minimum = plugin.minimumMinecraftVersion == null ? "-" : plugin.minimumMinecraftVersion;
            String maximum = plugin.maximumMinecraftVersion == null ? "+" : plugin.maximumMinecraftVersion;
            appendPart(summary, getString(R.string.native_plugins_minecraft_range, minimum, maximum));
        }
        appendPart(summary, plugin.packageName);
        return summary.toString();
    }

    private static void appendPart(StringBuilder builder, String value) {
        if (builder.length() > 0) builder.append(" · ");
        builder.append(value);
    }
}
