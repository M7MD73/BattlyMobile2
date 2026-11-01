package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class TouchControllerConstraintTest {
    public static class Fixture {
        public static int measure(int width) {
            return top.fifthlight.touchcontroller.relocated.kotlin.ranges.RangesKt.coerceIn(12, 0, width);
        }
    }

    @Test
    public void rewrittenMeasurementNoLongerThrowsOnNegativeAvailableWidth() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> Fixture.measure(-5));
        try (URLClassLoader agent = new URLClassLoader(new URL[]{new File(
                "src/main/assets/components/lwjgl3/lwjgl-patch-agent.jar").toURI().toURL()}, null)) {
            byte[] original;
            try (java.io.InputStream in = getClass().getResourceAsStream(
                    "/net/kdt/pojavlaunch/utils/TouchControllerConstraintTest$Fixture.class")) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                original = out.toByteArray();
            }
            java.lang.reflect.Method patch = agent.loadClass("net.kdt.patch.LwjglPatchAgent")
                    .getDeclaredMethod("patchTouchControllerTextConstraints", byte[].class);
            patch.setAccessible(true);
            byte[] rewritten = (byte[]) patch.invoke(null, original);
            assertNotNull(rewritten);
            Class<?> fixture = new ClassLoader(agent) {
                Class<?> define() { return defineClass(null, rewritten, 0, rewritten.length); }
            }.define();
            assertEquals(0, fixture.getMethod("measure", int.class).invoke(null, -5));
            assertEquals(12, fixture.getMethod("measure", int.class).invoke(null, 20));
        }
    }

    @Test
    public void packagedHelperHandlesNegativeTextWidthWithoutChangingValidRanges() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{new File(
                "src/main/assets/components/lwjgl3/lwjgl-patch-agent.jar").toURI().toURL()}, null)) {
            Class<?> helper = loader.loadClass("net.kdt.patch.TouchLayoutCompat");
            java.lang.reflect.Method clamp = helper.getMethod("coerceIn", int.class, int.class, int.class);
            assertEquals(0, clamp.invoke(null, 12, 0, -5));
            assertEquals(0, clamp.invoke(null, 12, 0, 0));
            assertEquals(12, clamp.invoke(null, 12, 0, 20));
            assertEquals(20, clamp.invoke(null, 40, 0, 20));
            assertEquals(0, clamp.invoke(null, -1, 0, 20));
            assertNotNull(loader.loadClass("net.kdt.patch.LwjglPatchAgent")
                    .getDeclaredMethod("patchTouchControllerTextConstraints", byte[].class));
        }
    }
}
