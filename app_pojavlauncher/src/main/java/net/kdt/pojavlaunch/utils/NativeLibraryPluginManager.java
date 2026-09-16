package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Discovers and activates APKs implementing Zalith/FCL's FCLNativePlugin contract. */
public final class NativeLibraryPluginManager {
    private static final String TAG = "NativeLibPlugin";
    private static final String META_PLUGIN = "FCLNativePlugin";
    private static final String PREFS = "battly_native_library_plugins";
    private static final String PREF_DISABLED = "disabled_packages";
    private static volatile List<Plugin> sActivePlugins = Collections.emptyList();

    private NativeLibraryPluginManager() {
    }

    @NonNull
    public static List<Plugin> discover(@NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved;
        if (Build.VERSION.SDK_INT >= 33) {
            resolved = packageManager.queryIntentActivities(
                    launcherIntent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA));
        } else {
            //noinspection deprecation
            resolved = packageManager.queryIntentActivities(launcherIntent, PackageManager.GET_META_DATA);
        }

        List<Plugin> plugins = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo resolveInfo : resolved) {
            if (resolveInfo.activityInfo == null || resolveInfo.activityInfo.applicationInfo == null) continue;
            ApplicationInfo info = resolveInfo.activityInfo.applicationInfo;
            if (!seen.add(info.packageName) || (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            Bundle metadata = info.metaData;
            if (metadata == null || !metadata.getBoolean(META_PLUGIN, false)) continue;
            try {
                File nativeDirectory = new File(info.nativeLibraryDir == null ? "" : info.nativeLibraryDir);
                if (!containsNativeLibrary(nativeDirectory)) {
                    Log.w(TAG, "Ignoring plugin without extracted native libraries: " + info.packageName);
                    continue;
                }
                String versionName = packageManager.getPackageInfo(info.packageName, 0).versionName;
                plugins.add(new Plugin(
                        info.packageName,
                        String.valueOf(info.loadLabel(packageManager)),
                        versionName == null ? "" : versionName,
                        metadata.getString("des", ""),
                        metadataValue(metadata, "minMCVer"),
                        metadataValue(metadata, "maxMCVer"),
                        nativeDirectory,
                        NativeLibraryPluginPolicy.parseJvmProperties(
                                metadata.getString("environment", ""), nativeDirectory)
                ));
            } catch (Exception exception) {
                Log.w(TAG, "Could not inspect " + info.packageName, exception);
            }
        }
        plugins.sort(Comparator.comparing(plugin -> plugin.appName.toLowerCase(java.util.Locale.ROOT)));
        return Collections.unmodifiableList(plugins);
    }

    public static void prepareForLaunch(@NonNull Context context, @Nullable String minecraftVersion) {
        Set<String> disabled = disabledPackages(context);
        List<Plugin> active = new ArrayList<>();
        for (Plugin plugin : discover(context)) {
            if (disabled.contains(plugin.packageName)
                    || !NativeLibraryPluginPolicy.supportsMinecraftVersion(
                    minecraftVersion, plugin.minimumMinecraftVersion, plugin.maximumMinecraftVersion)) {
                continue;
            }
            active.add(plugin);
            Logger.appendToLog("Info: Native library plugin enabled: " + plugin.appName
                    + " " + plugin.versionName + " (" + plugin.packageName + ")");
        }
        sActivePlugins = Collections.unmodifiableList(active);
    }

    public static List<String> activeNativePaths() {
        List<String> paths = new ArrayList<>();
        for (Plugin plugin : sActivePlugins) paths.add(plugin.nativeDirectory.getAbsolutePath());
        return paths;
    }

    public static List<String> activeJvmArguments() {
        List<String> arguments = new ArrayList<>();
        for (Plugin plugin : sActivePlugins) arguments.addAll(plugin.jvmArguments);
        return arguments;
    }

    public static boolean supportsDistantHorizons(@NonNull Context context,
                                                   @Nullable String minecraftVersion) {
        Set<String> disabled = disabledPackages(context);
        List<Plugin> discovered = discover(context);
        for (Plugin plugin : discovered) {
            if (disabled.contains(plugin.packageName)
                    || !NativeLibraryPluginPolicy.supportsMinecraftVersion(
                    minecraftVersion, plugin.minimumMinecraftVersion, plugin.maximumMinecraftVersion)) {
                continue;
            }
            if (hasDistantHorizonsZstdOverride(plugin.jvmArguments)) {
                Log.i(TAG, "Distant Horizons native support detected in " + plugin.packageName);
                return true;
            }
        }
        Log.i(TAG, "No compatible Distant Horizons native plugin found; discovered="
                + discovered.size() + ", disabled=" + disabled.size()
                + ", minecraftVersion=" + minecraftVersion);
        return false;
    }

    static boolean hasDistantHorizonsZstdOverride(List<String> arguments) {
        if (arguments == null) return false;
        for (String argument : arguments) {
            if (argument == null) continue;
            String lower = argument.toLowerCase(java.util.Locale.ROOT);
            if (!lower.startsWith("-dzstdnativepath=")
                    && !lower.startsWith("-dzstd.jni.path=")) continue;
            int separator = argument.indexOf('=');
            if (separator > 0 && separator + 1 < argument.length()
                    && new File(argument.substring(separator + 1)).isFile()) return true;
        }
        return false;
    }

    public static boolean isEnabled(@NonNull Context context, @NonNull String packageName) {
        return !disabledPackages(context).contains(packageName);
    }

    public static void setEnabled(@NonNull Context context, @NonNull String packageName, boolean enabled) {
        Set<String> disabled = new LinkedHashSet<>(disabledPackages(context));
        if (enabled) disabled.remove(packageName); else disabled.add(packageName);
        preferences(context).edit().putStringSet(PREF_DISABLED, disabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> disabledPackages(Context context) {
        Set<String> stored = preferences(context).getStringSet(PREF_DISABLED, Collections.emptySet());
        return stored == null ? Collections.emptySet() : new LinkedHashSet<>(stored);
    }

    private static boolean containsNativeLibrary(File directory) {
        if (!directory.isDirectory()) return false;
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".so"));
        return files != null && files.length > 0;
    }

    @Nullable
    private static String metadataValue(Bundle metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static final class Plugin {
        public final String packageName;
        public final String appName;
        public final String versionName;
        public final String description;
        @Nullable public final String minimumMinecraftVersion;
        @Nullable public final String maximumMinecraftVersion;
        public final File nativeDirectory;
        public final List<String> jvmArguments;

        private Plugin(String packageName, String appName, String versionName, String description,
                       @Nullable String minimumMinecraftVersion,
                       @Nullable String maximumMinecraftVersion,
                       File nativeDirectory, List<String> jvmArguments) {
            this.packageName = packageName;
            this.appName = appName;
            this.versionName = versionName;
            this.description = description;
            this.minimumMinecraftVersion = minimumMinecraftVersion;
            this.maximumMinecraftVersion = maximumMinecraftVersion;
            this.nativeDirectory = nativeDirectory;
            this.jvmArguments = jvmArguments;
        }
    }
}
