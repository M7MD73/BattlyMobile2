package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MinecraftTextInputAgentTest {
    @Test
    public void packagedLwjglVersionsContainAutomaticTextInputBridgeDependencies() throws Exception {
        for (String version : new String[]{"3.3.3", "3.4.1", "3.4.2"}) {
            File lwjgl = new File("src/main/assets/components/lwjgl3/" + version
                    + "/lwjgl-" + version + "-merged-modules.jar");
            assertTrue("Missing packaged LWJGL " + version, lwjgl.isFile());
            try (JarFile jar = new JarFile(lwjgl)) {
                assertTrue("Missing ArrayMap in LWJGL " + version,
                        jar.getJarEntry("android/util/ArrayMap.class") != null);
                JarEntry glfw = jar.getJarEntry("org/lwjgl/glfw/GLFW.class");
                assertTrue("Missing GLFW in LWJGL " + version, glfw != null);
                assertTrue(readClassConstants(jar, glfw).contains("setAndroidTextInputActive"));
            }
        }
    }

    @Test
    public void packagedAgentContainsAutomaticTextInputBridge() throws Exception {
        File agent = new File("src/main/assets/components/lwjgl3/lwjgl-patch-agent.jar");
        assertTrue("Missing packaged LWJGL patch agent", agent.isFile());

        try (JarFile jar = new JarFile(agent)) {
            assertTrue(jar.getJarEntry("net/kdt/patch/TextInputBridge.class") != null);
            JarEntry transformer = jar.getJarEntry("net/kdt/patch/LwjglPatchAgent.class");
            assertTrue(transformer != null);
            try (InputStream input = jar.getInputStream(transformer)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                String classConstants = new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
                assertTrue(classConstants.contains("net/minecraft/client/gui/components/EditBox"));
                assertTrue(classConstants.contains("net/minecraft/client/gui/components/MultiLineEditBox"));
            }
            try (InputStream input = jar.getInputStream(
                    jar.getJarEntry("net/kdt/patch/TextInputBridge.class"))) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                String classConstants = new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
                assertTrue(classConstants.contains("onTextFocusChanged"));
                assertTrue(classConstants.contains("setAndroidTextInputActive"));
            }
        }
    }

    private static String readClassConstants(JarFile jar, JarEntry entry) throws Exception {
        try (InputStream input = jar.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
