package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.FloatBuffer;
import static org.junit.Assert.*;

public class GlfwWindowContentScaleTest {
    @Test public void allPackagedLwjglChannelsExposeBothScaleOverloads() throws Exception {
        for (String version : new String[]{"3.3.3", "3.4.1", "3.4.2"}) {
            File file = new File("src/main/assets/components/lwjgl3/" + version
                    + "/lwjgl-" + version + "-merged-modules.jar");
            try (URLClassLoader loader = new URLClassLoader(new URL[]{file.toURI().toURL(),
                    new File("../jre_lwjgl3glfw/libs/lwjgl.jar").toURI().toURL()}, null)) {
                Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", false, loader);
                assertNotNull(glfw.getDeclaredMethod("glfwGetWindowContentScale", long.class, float[].class, float[].class));
                assertNotNull(glfw.getDeclaredMethod("glfwGetWindowContentScale", long.class, FloatBuffer.class, FloatBuffer.class));
            }
        }
    }
}
