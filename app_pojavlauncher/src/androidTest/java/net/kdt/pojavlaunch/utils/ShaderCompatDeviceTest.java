package net.kdt.pojavlaunch.utils;

import android.app.Instrumentation;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.LauncherActivity;
import com.google.gson.JsonParser;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShaderCompatDeviceTest {
    private static final String PROGRAM = "assets/minecraft/shaders/post/entity_outline.json";

    @Test public void repairsPoisonedClientAndFabricCacheOnAndroid() throws Exception {
        Instrumentation i = InstrumentationRegistry.getInstrumentation();
        i.startActivitySync(new Intent(i.getTargetContext(), LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        File root = new File(i.getTargetContext().getCacheDir(), "shader-regression-" + System.nanoTime());
        assertTrue(root.mkdirs());
        File client = new File(root, "client.jar");
        File cache = new File(root, "client-intermediary.jar");
        Method patch = VanillaPostShaderCompat.class.getDeclaredMethod("patchCandidate", File.class, Set.class, boolean.class);
        patch.setAccessible(true);
        Method repair = VanillaPostShaderCompat.class.getDeclaredMethod("repairRemappedJars", File.class, Set.class, boolean.class);
        repair.setAccessible(true);
        try {
            for (File jar : new File[]{client, cache}) {
                try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
                    entry(out, PROGRAM, "{\"battly_android_disabled\":true,\"targets\":[],\"passes\":[]}");
                    entry(out, "assets/minecraft/post_effect/entity_outline.json", "{\"targets\":{},\"passes\":[]}");
                    entry(out, "assets/custom/keep.txt", "untouched");
                }
                File oldMarker = new File(root, jar.getName() + ".battly-android-postshader-fix4");
                assertTrue(oldMarker.createNewFile());
                assertTrue(oldMarker.setLastModified(jar.lastModified() + 1000));
            }
            patch.invoke(null, client, new HashSet<String>(), true);
            repair.invoke(null, root, new HashSet<String>(), true);
            for (File file : new File[]{client, cache}) {
                try (JarFile jar = new JarFile(file)) {
                    String program = read(jar, PROGRAM);
                    assertEquals("minecraft:post/entity_sobel", JsonParser.parseString(program)
                            .getAsJsonObject().get("fragment").getAsString());
                    assertFalse(program.contains("battly_android_disabled"));
                    assertEquals("untouched", read(jar, "assets/custom/keep.txt"));
                }
                long modified = file.lastModified();
                patch.invoke(null, file, new HashSet<String>(), true);
                assertEquals(modified, file.lastModified());
                assertFalse(new File(root, file.getName() + ".battly-tmp").exists());
            }
        } finally {
            File[] files = root.listFiles();
            if (files != null) for (File file : files) file.delete();
            root.delete();
        }
    }

    private static void entry(JarOutputStream out, String name, String data) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(data.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static String read(JarFile jar, String entry) throws IOException {
        try (InputStream in = jar.getInputStream(jar.getJarEntry(entry));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }
}
