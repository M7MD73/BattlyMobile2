package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.NativeDependencyCatalog;
import net.kdt.pojavlaunch.utils.NativeLibraryPluginManager;

import java.util.List;

public class LauncherPreferenceNativePluginsFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        PreferenceCategory bundledCategory = new PreferenceCategory(requireContext());
        bundledCategory.setKey("bundled_native_dependencies");
        bundledCategory.setTitle(R.string.native_plugins_bundled_category);
        screen.addPreference(bundledCategory);
        List<NativeDependencyCatalog.Dependency> bundledDependencies =
                NativeLibraryPluginManager.bundledDependencies(requireContext());
        for (NativeDependencyCatalog.Dependency dependency : bundledDependencies) {
            Preference preference = new Preference(requireContext());
            preference.setSelectable(false);
            preference.setTitle(dependency.name);
            preference.setSummary(getString(
                    R.string.native_plugins_bundled_ready, dependency.version));
            bundledCategory.addPreference(preference);
        }

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
        } else {
            for (NativeLibraryPluginManager.Plugin plugin : plugins) {
                SwitchPreferenceCompat preference = new SwitchPreferenceCompat(requireContext());
                preference.setKey("native_plugin_" + plugin.packageName);
                preference.setPersistent(false);
                preference.setTitle(plugin.appName);
                preference.setSummary(buildSummary(plugin));
                preference.setChecked(NativeLibraryPluginManager.isEnabled(
                        requireContext(), plugin.packageName));
                preference.setOnPreferenceChangeListener((ignored, newValue) -> {
                    if (!Boolean.TRUE.equals(newValue)) {
                        return NativeLibraryPluginManager.setEnabled(requireContext(), plugin.packageName,
                                plugin.signingIdentity, false);
                    }
                    Tools.createStyledDialogBuilder(requireContext())
                            .setTitle(plugin.appName)
                            .setMessage(R.string.native_plugins_trust_warning)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.native_plugins_trust_action, (dialog, which) -> {
                                if (!isAdded()) return;
                                preference.setChecked(NativeLibraryPluginManager.setEnabled(requireContext(),
                                        plugin.packageName, plugin.signingIdentity, true));
                            }).show();
                    return false;
                });
                category.addPreference(preference);
            }
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
