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
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/** Discovers and activates APKs implementing Zalith/FCL's FCLNativePlugin contract. */
public final class NativeLibraryPluginManager {
    private static final String TAG = "NativeLibPlugin";
    private static final String META_PLUGIN = "FCLNativePlugin";
    private static final String PREFS = "battly_native_library_plugins";
    private static final String PREF_APPROVED = "approved_signers_v1";
    private static volatile List<Plugin> sActivePlugins = Collections.emptyList();
    private static volatile List<NativeDependencyCatalog.Dependency> sBundledDependencies =
            Collections.emptyList();
    private static volatile List<File> sBundledClasspathFiles = Collections.emptyList();
    private static volatile Set<String> sActiveCapabilities = Collections.emptySet();

    private NativeLibraryPluginManager() {
    }

    @NonNull
    public static List<Plugin> discover(@NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        List<ResolveInfo> resolved;
        long queryFlags = PackageManager.GET_META_DATA | PackageManager.GET_SHARED_LIBRARY_FILES;
        if (Build.VERSION.SDK_INT >= 33) {
            resolved = packageManager.queryIntentActivities(
                    launcherIntent, PackageManager.ResolveInfoFlags.of(queryFlags));
        } else {
            //noinspection deprecation
            resolved = packageManager.queryIntentActivities(launcherIntent, (int) queryFlags);
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
                List<String> jvmArguments = NativeLibraryPluginPolicy.parseJvmProperties(
                        metadata.getString("environment", ""), nativeDirectory);
                plugins.add(new Plugin(
                        info.packageName,
                        String.valueOf(info.loadLabel(packageManager)),
                        versionName == null ? "" : versionName,
                        metadata.getString("des", ""),
                        metadataValue(metadata, "minMCVer"),
                        metadataValue(metadata, "maxMCVer"),
                        nativeDirectory,
                        signingIdentity(context, info.packageName),
                        jvmArguments,
                        NativeDependencyCatalog.inferCapabilities(jvmArguments)
                ));
            } catch (Exception exception) {
                Log.w(TAG, "Could not inspect " + info.packageName, exception);
            }
        }
        plugins.sort(Comparator.comparing(plugin -> plugin.appName.toLowerCase(java.util.Locale.ROOT)));
        return Collections.unmodifiableList(plugins);
    }

    public static void prepareForLaunch(@NonNull Context context, @Nullable String minecraftVersion) {
        prepareForLaunch(context, minecraftVersion, null);
    }

    public static void prepareForLaunch(@NonNull Context context, @Nullable String minecraftVersion,
                                        @Nullable File gameDirectory) {
        Set<String> approved = approvedPlugins(context);
        List<Plugin> active = new ArrayList<>();
        Set<String> capabilities = new LinkedHashSet<>();
        Set<String> requiredCapabilities = gameDirectory == null
                ? null : AndroidNativeModDetector.requiredCapabilitiesFromDirectory(gameDirectory);
        List<NativeDependencyCatalog.Dependency> bundled = bundledDependencies(context);
        List<NativeDependencyCatalog.Dependency> activeBundled = new ArrayList<>();
        List<File> bundledClasspath = new ArrayList<>();
        for (NativeDependencyCatalog.Dependency dependency : bundled) {
            if (requiredCapabilities != null
                    && !requiredCapabilities.contains(dependency.capability)) {
                continue;
            }
            List<File> dependencyClasspath = prepareClasspathArtifacts(context, dependency);
            if (dependencyClasspath == null) {
                Logger.appendToLog("Warning: Bundled Android dependency is incomplete and was skipped: "
                        + dependency.name);
                continue;
            }
            activeBundled.add(dependency);
            bundledClasspath.addAll(dependencyClasspath);
            capabilities.add(dependency.capability);
            Logger.appendToLog("Info: Bundled Android native dependency enabled: "
                    + dependency.name + " " + dependency.version);
        }
        for (Plugin plugin : discover(context)) {
            if (!NativePluginTrust.isApproved(approved, plugin.packageName, plugin.signingIdentity)
                    || !NativeLibraryPluginPolicy.supportsMinecraftVersion(
                    minecraftVersion, plugin.minimumMinecraftVersion, plugin.maximumMinecraftVersion)) {
                continue;
            }
            active.add(plugin);
            capabilities.addAll(plugin.capabilities);
            Logger.appendToLog("Info: Native library plugin enabled: " + plugin.appName
                    + " " + plugin.versionName + " (" + plugin.packageName + ")"
                    + (plugin.capabilities.isEmpty() ? "" : " capabilities=" + plugin.capabilities));
        }
        sBundledDependencies = Collections.unmodifiableList(activeBundled);
        sBundledClasspathFiles = Collections.unmodifiableList(bundledClasspath);
        sActivePlugins = Collections.unmodifiableList(active);
        sActiveCapabilities = Collections.unmodifiableSet(capabilities);
    }

    public static List<String> activeNativePaths() {
        List<String> paths = new ArrayList<>();
        for (Plugin plugin : sActivePlugins) paths.add(plugin.nativeDirectory.getAbsolutePath());
        return paths;
    }

    public static List<String> activeJvmArguments() {
        List<String> arguments = new ArrayList<>();
        for (NativeDependencyCatalog.Dependency dependency : sBundledDependencies) {
            arguments.addAll(dependency.jvmArguments);
        }
        for (Plugin plugin : sActivePlugins) arguments.addAll(plugin.jvmArguments);
        return arguments;
    }

    @NonNull
    public static List<File> activeClasspathFiles() {
        return sBundledClasspathFiles;
    }

    @NonNull
    public static Set<String> activeCapabilities() {
        return sActiveCapabilities;
    }

    @NonNull
    public static List<NativeDependencyCatalog.Dependency> bundledDependencies(
            @NonNull Context context) {
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        if (nativeLibraryDir == null || nativeLibraryDir.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return NativeDependencyCatalog.bundledDependencies(new File(nativeLibraryDir));
    }

    @NonNull
    public static Set<String> availableCapabilities(@NonNull Context context,
                                                     @Nullable String minecraftVersion) {
        Set<String> capabilities = new LinkedHashSet<>();
        for (NativeDependencyCatalog.Dependency dependency : bundledDependencies(context)) {
            if (hasPackagedClasspathArtifacts(context, dependency)) {
                capabilities.add(dependency.capability);
            }
        }
        Set<String> approved = approvedPlugins(context);
        for (Plugin plugin : discover(context)) {
            if (NativePluginTrust.isApproved(approved, plugin.packageName, plugin.signingIdentity)
                    && NativeLibraryPluginPolicy.supportsMinecraftVersion(
                    minecraftVersion, plugin.minimumMinecraftVersion,
                    plugin.maximumMinecraftVersion)) {
                capabilities.addAll(plugin.capabilities);
            }
        }
        return Collections.unmodifiableSet(capabilities);
    }

    @Nullable
    private static List<File> prepareClasspathArtifacts(
            Context context, NativeDependencyCatalog.Dependency dependency) {
        if (dependency.classpathArtifacts.isEmpty()) return Collections.emptyList();
        File outputDirectory = new File(context.getCacheDir(), "native-dependencies");
        List<File> files = new ArrayList<>();
        for (NativeDependencyCatalog.ClasspathArtifact artifact : dependency.classpathArtifacts) {
            File output = new File(outputDirectory, artifact.outputName);
            try {
                Tools.copyAssetFile(context, artifact.assetPath,
                        outputDirectory.getAbsolutePath(), artifact.outputName, true);
                try (JarFile jarFile = new JarFile(output)) {
                    if (jarFile.getEntry(artifact.requiredClassEntry) == null) {
                        throw new IOException("Missing " + artifact.requiredClassEntry);
                    }
                }
                files.add(output);
            } catch (IOException | RuntimeException exception) {
                Log.w(TAG, "Could not prepare classpath artifact " + artifact.assetPath, exception);
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                return null;
            }
        }
        return files;
    }

    private static boolean hasPackagedClasspathArtifacts(
            Context context, NativeDependencyCatalog.Dependency dependency) {
        for (NativeDependencyCatalog.ClasspathArtifact artifact : dependency.classpathArtifacts) {
            try (InputStream ignored = context.getAssets().open(artifact.assetPath)) {
                // Asset exists.
            } catch (IOException exception) {
                return false;
            }
        }
        return true;
    }

    public static boolean supportsDistantHorizons(@NonNull Context context,
                                                   @Nullable String minecraftVersion) {
        boolean supported = availableCapabilities(context, minecraftVersion).contains(
                NativeDependencyCatalog.CAPABILITY_DISTANT_HORIZONS_ZSTD);
        Log.i(TAG, "Distant Horizons Android Zstd support=" + supported
                + ", minecraftVersion=" + minecraftVersion);
        return supported;
    }

    static boolean hasDistantHorizonsZstdOverride(List<String> arguments) {
        return arguments != null && NativeDependencyCatalog.inferCapabilities(arguments).contains(
                NativeDependencyCatalog.CAPABILITY_DISTANT_HORIZONS_ZSTD);
    }

    public static boolean isEnabled(@NonNull Context context, @NonNull String packageName) {
        return NativePluginTrust.isApproved(approvedPlugins(context), packageName, signingIdentity(context, packageName));
    }

    public static boolean setEnabled(@NonNull Context context, @NonNull String packageName,
                                     @NonNull String expectedSigner, boolean enabled) {
        Set<String> approved = approvedPlugins(context);
        approved.removeIf(entry -> entry.startsWith(packageName + "|"));
        if (enabled) {
            String currentSigner = signingIdentity(context, packageName);
            String key = NativePluginTrust.key(packageName, currentSigner);
            if (key == null || !currentSigner.equals(expectedSigner)) return false;
            approved.add(key);
        }
        return preferences(context).edit().putStringSet(PREF_APPROVED, approved).commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> approvedPlugins(Context context) {
        Set<String> stored = preferences(context).getStringSet(PREF_APPROVED, Collections.emptySet());
        return stored == null ? Collections.emptySet() : new LinkedHashSet<>(stored);
    }

    @SuppressWarnings("deprecation")
    private static String signingIdentity(Context context, String packageName) {
        try {
            PackageManager manager = context.getPackageManager();
            android.content.pm.Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                android.content.pm.PackageInfo info = manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return "";
                signatures = info.signingInfo.getApkContentsSigners();
            } else {
                signatures = manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures;
            }
            if (signatures == null || signatures.length == 0) return "";
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            List<String> signers = new ArrayList<>();
            for (android.content.pm.Signature signature : signatures) {
                signers.add(hex(digest.digest(signature.toByteArray())));
            }
            Collections.sort(signers);
            return hex(digest.digest(android.text.TextUtils.join(",", signers).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception unavailable) { return ""; }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte item : value) result.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return result.toString();
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
        public final String signingIdentity;
        public final List<String> jvmArguments;
        public final Set<String> capabilities;

        private Plugin(String packageName, String appName, String versionName, String description,
                       @Nullable String minimumMinecraftVersion,
                       @Nullable String maximumMinecraftVersion,
                       File nativeDirectory, String signingIdentity, List<String> jvmArguments,
                       Set<String> capabilities) {
            this.packageName = packageName;
            this.appName = appName;
            this.versionName = versionName;
            this.description = description;
            this.minimumMinecraftVersion = minimumMinecraftVersion;
            this.maximumMinecraftVersion = maximumMinecraftVersion;
            this.nativeDirectory = nativeDirectory;
            this.signingIdentity = signingIdentity;
            this.jvmArguments = jvmArguments;
            this.capabilities = capabilities;
        }
    }
}
