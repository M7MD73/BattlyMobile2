package net.kdt.pojavlaunch.customcontrols.mouse;

import org.junit.Test;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import static org.junit.Assert.*;

public class LegacyMouseButtonsTest {
    @Test public void packagedLwjglxAcceptsEveryAdvertisedButton() throws Exception {
        for (String version : new String[]{"3.3.3", "3.4.1", "common"}) {
            File bridge = new File("src/main/assets/components/lwjgl3/lwjgl-glfw-classes.jar");
            String modulesVersion = "common".equals(version) ? "3.3.3" : version;
            File jar = "common".equals(version) ? bridge : new File(
                    "src/main/assets/components/lwjgl3/" + version + "/lwjgl-lwjglx.jar");
            File modules = new File("src/main/assets/components/lwjgl3/" + modulesVersion
                    + "/lwjgl-" + modulesVersion + "-merged-modules.jar");
            try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL(),
                    bridge.toURI().toURL(), modules.toURI().toURL()}, null)) {
                Class<?> type = loader.loadClass("org.lwjgl.input.GLFWInputImplementation");
                Object input = type.getConstructor().newInstance();
                int count = (Integer) type.getMethod("getButtonCount").invoke(input);
                byte[] buttons = (byte[]) type.getField("mouse_buffer").get(input);
                assertTrue(version, count >= 5);
                assertTrue(version, buttons.length >= count);
                type.getField("grab").setBoolean(input, true);
                java.lang.reflect.Method send = type.getMethod("putMouseEventWithCoords",
                        byte.class, byte.class, int.class, int.class, int.class, long.class);
                for (int button = 0; button < count; button++) {
                    send.invoke(input, (byte) button, (byte) 1, 0, 0, 0, 0L);
                    assertEquals(1, buttons[button]);
                    send.invoke(input, (byte) button, (byte) 0, 0, 0, 0, 0L);
                    assertEquals(0, buttons[button]);
                }
            }
        }
    }
}
