package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves Android replacements for native dependencies normally bundled for desktop PCs. */
public final class NativeDependencyCatalog {
    public static final String CAPABILITY_DISTANT_HORIZONS_ZSTD = "distant_horizons_zstd";
    public static final String CAPABILITY_SQLITE_JDBC_ANDROID = "sqlite_jdbc_android";
    public static final String CAPABILITY_IMGUI_JAVA = "imgui_java";
    public static final String CAPABILITY_IMGUI_MOULBERRY92 = "imgui_moulberry92";
    public static final String CAPABILITY_SABLE_RAPIER = "sable_rapier";
    public static final String CAPABILITY_VOXY_NATIVE = "voxy_native";

    private static final String ZSTD_LIBRARY = "libzstd-jni-1.5.7-6-dhcompat.so";
    private static final String SQLITE_JDBC_LIBRARY = "libsqlitejdbc-dh.so";
    private static final String IMGUI_LIBRARY = "libimgui-java.so";

    private NativeDependencyCatalog() {
    }

    @NonNull
    public static List<Dependency> bundledDependencies(@NonNull File nativeDirectory) {
        List<Dependency> dependencies = new ArrayList<>();
        for (String codec : new String[]{"opus4j", "rnnoise4j", "speex4j", "lame4j"}) {
            File library = new File(nativeDirectory, "lib" + codec + ".so");
            if (library.isFile()) {
                dependencies.add(new Dependency("codec_" + codec, codec + " Android", "jni-contract-v1",
                        library, Collections.singletonList("-Dbattly.codec." + codec + ".path="
                                + library.getAbsolutePath()), Collections.emptyList()));
            }
        }
        File zstd = new File(nativeDirectory, ZSTD_LIBRARY);
        if (zstd.isFile()) {
            dependencies.add(new Dependency(
                    CAPABILITY_DISTANT_HORIZONS_ZSTD,
                    "Distant Horizons Zstd",
                    "1.5.7-6-dhcompat",
                    zstd,
                    Collections.singletonList("-DZstdNativePath=" + zstd.getAbsolutePath()),
                    Collections.emptyList()));
        }

        File imgui = new File(nativeDirectory, IMGUI_LIBRARY);
        if (imgui.isFile()) {
            List<String> arguments = new ArrayList<>();
            arguments.add("-Dimgui.library.path=" + nativeDirectory.getAbsolutePath());
            arguments.add("-Dimgui.library.name=" + IMGUI_LIBRARY);
            dependencies.add(new Dependency(
                    CAPABILITY_IMGUI_JAVA,
                    "ImGui Java",
                    "1.86.12-patched",
                    imgui,
                    arguments,
                    Collections.emptyList()));
        }

        File sqliteJdbc = new File(nativeDirectory, SQLITE_JDBC_LIBRARY);
        if (sqliteJdbc.isFile()) {
            List<String> arguments = new ArrayList<>();
            arguments.add("-Ddh_sqlite.lib.path=" + nativeDirectory.getAbsolutePath());
            arguments.add("-Ddh_sqlite.lib.name=" + SQLITE_JDBC_LIBRARY);
            dependencies.add(new Dependency(
                    CAPABILITY_SQLITE_JDBC_ANDROID,
                    "Distant Horizons SQLite JDBC Android",
                    "3.53.2.1",
                    sqliteJdbc,
                    arguments,
                    Collections.emptyList()));
        }
        File imgui92 = new File(nativeDirectory, "libimgui-moulberry92-java.so");
        if (imgui92.isFile()) {
            dependencies.add(new Dependency(
                    CAPABILITY_IMGUI_MOULBERRY92, "ImGui Moulberry 92", "1.92-moulberry.2",
                    imgui92, Collections.singletonList(
                            "-Dbattly.imgui.moulberry92.path=" + imgui92.getAbsolutePath()),
                    Collections.emptyList()));
        }
        return Collections.unmodifiableList(dependencies);
    }

    @NonNull
    public static Set<String> inferCapabilities(@NonNull List<String> jvmArguments) {
        Map<String, String> properties = parseProperties(jvmArguments);
        Set<String> capabilities = new LinkedHashSet<>();
        for (String codec : new String[]{"opus4j", "rnnoise4j", "speex4j", "lame4j"}) {
            if (isFile(properties.get("battly.codec." + codec + ".path"))) capabilities.add("codec_" + codec);
        }

        if (isFile(properties.get("zstdnativepath"))
                || isFile(properties.get("zstd.jni.path"))) {
            capabilities.add(CAPABILITY_DISTANT_HORIZONS_ZSTD);
        }

        String imguiPath = properties.get("imgui.library.path");
        String imguiName = properties.get("imgui.library.name");
        if (imguiPath != null && imguiName != null
                && new File(imguiPath, imguiName).isFile()) {
            capabilities.add(CAPABILITY_IMGUI_JAVA);
        }
        if (isFile(properties.get("battly.imgui.moulberry92.path"))) {
            capabilities.add(CAPABILITY_IMGUI_MOULBERRY92);
        }

        if (propertyLibraryExists(properties, "dh_sqlite.lib.path", "dh_sqlite.lib.name")) {
            capabilities.add(CAPABILITY_SQLITE_JDBC_ANDROID);
        }

        if (isFile(properties.get("sable_rapier_path"))) {
            capabilities.add(CAPABILITY_SABLE_RAPIER);
        }
        if (isFile(properties.get("voxy.native.path"))
                || isFile(properties.get("voxy_native_path"))) {
            capabilities.add(CAPABILITY_VOXY_NATIVE);
        }
        return Collections.unmodifiableSet(capabilities);
    }

    @NonNull
    private static Map<String, String> parseProperties(List<String> arguments) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (String argument : arguments) {
            if (argument == null || !argument.startsWith("-D")) continue;
            int separator = argument.indexOf('=');
            if (separator <= 2 || separator + 1 >= argument.length()) continue;
            String key = argument.substring(2, separator).toLowerCase(Locale.ROOT);
            properties.put(key, argument.substring(separator + 1));
        }
        return properties;
    }

    private static boolean isFile(String path) {
        return path != null && new File(path).isFile();
    }

    private static boolean propertyLibraryExists(Map<String, String> properties,
                                                 String pathKey, String nameKey) {
        String directory = properties.get(pathKey);
        String name = properties.get(nameKey);
        return directory != null && name != null && new File(directory, name).isFile();
    }

    public static final class Dependency {
        public final String capability;
        public final String name;
        public final String version;
        public final File library;
        public final List<String> jvmArguments;
        public final List<ClasspathArtifact> classpathArtifacts;

        private Dependency(String capability, String name, String version,
                           File library, List<String> jvmArguments,
                           List<ClasspathArtifact> classpathArtifacts) {
            this.capability = capability;
            this.name = name;
            this.version = version;
            this.library = library;
            this.jvmArguments = Collections.unmodifiableList(new ArrayList<>(jvmArguments));
            this.classpathArtifacts = Collections.unmodifiableList(
                    new ArrayList<>(classpathArtifacts));
        }
    }

    public static final class ClasspathArtifact {
        public final String assetPath;
        public final String outputName;
        public final String requiredClassEntry;

        private ClasspathArtifact(String assetPath, String outputName,
                                  String requiredClassEntry) {
            this.assetPath = assetPath;
            this.outputName = outputName;
            this.requiredClassEntry = requiredClassEntry;
        }
    }
}
