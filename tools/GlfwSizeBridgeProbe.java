import java.io.*;
import java.net.*;
import java.nio.*;
import java.lang.reflect.*;
import java.util.*;
import net.kdt.patch.asm.*;

/** Runs real packaged size methods, omitting only Android/native initialization. */
public final class GlfwSizeBridgeProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("org.lwjgl.util.NoChecks", "true");
        for (String channel : new String[]{"3.3.3", "3.4.1", "3.4.2"}) {
            URL modules = new File(args[0] + "/app_pojavlauncher/src/main/assets/components/lwjgl3/"
                    + channel + "/lwjgl-" + channel + "-merged-modules.jar").toURI().toURL();
            try (URLClassLoader loader = new URLClassLoader(new URL[]{modules,
                    new File(args[0] + "/jre_lwjgl3glfw/libs/lwjgl.jar").toURI().toURL()}, null) {
                @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (!name.equals("org.lwjgl.glfw.GLFW")) return super.findClass(name);
                    try (InputStream in = findResource(name.replace('.', '/') + ".class").openStream()) {
                        ClassReader reader = new ClassReader(in);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                                if (n.equals("<clinit>")) return null;
                                return super.visitMethod(a, n, d, s, e);
                            }
                        }, 0);
                        byte[] bytes = writer.toByteArray();
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception ex) { throw new ClassNotFoundException(name, ex); }
                }
            }) {
                Class<?> glfw = loader.loadClass("org.lwjgl.glfw.GLFW");
                Class<?> mapType = loader.loadClass("android.util.ArrayMap");
                Object map = mapType.newInstance();
                Field mapField = glfw.getDeclaredField("mGLFWWindowMap"); mapField.setAccessible(true); mapField.set(null, map);
                Object window = loader.loadClass("org.lwjgl.glfw.GLFWWindowProperties").newInstance();
                mapType.getMethod("put", Object.class, Object.class).invoke(map, 1L, window);
                Object helper = loader.loadClass("org.lwjgl.glfw.GLFWWindowProperties").newInstance();
                helper.getClass().getField("width").setInt(helper, 1);
                helper.getClass().getField("height").setInt(helper, 1);
                mapType.getMethod("put", Object.class, Object.class).invoke(map, 2L, helper);
                glfw.getMethod("internalChangeMonitorSize", int.class, int.class).invoke(null, 1280, 720);
                for (String query : new String[]{"glfwGetWindowSize", "glfwGetFramebufferSize"}) {
                    Method bufferQuery = glfw.getMethod(query, long.class, IntBuffer.class, IntBuffer.class);
                    IntBuffer w = IntBuffer.allocate(3); w.position(1);
                    IntBuffer h = IntBuffer.allocate(3); h.position(1);
                    for (int repeat = 0; repeat < 10; repeat++) bufferQuery.invoke(null, 1L, w, h);
                    if (w.position() != 1 || h.position() != 1 || w.get(1) != 1280 || h.get(1) != 720) throw new AssertionError(query);
                    bufferQuery.invoke(null, 1L, null, h);
                    Method arrayQuery = glfw.getMethod(query, long.class, int[].class, int[].class);
                    int[] a = new int[1]; arrayQuery.invoke(null, 1L, a, null);
                    if (a[0] != 1280) throw new AssertionError("array " + query);
                }
                glfw.getMethod("internalChangeMonitorSize", int.class, int.class).invoke(null, 960, 540);
                int[] w = new int[1], h = new int[1];
                glfw.getMethod("glfwGetWindowSize", long.class, int[].class, int[].class).invoke(null, 1L, w, h);
                if (w[0] != 960 || h[0] != 540) throw new AssertionError("stale dimensions");
                if (helper.getClass().getField("width").getInt(helper) != 1
                        || helper.getClass().getField("height").getInt(helper) != 1) throw new AssertionError("hidden helper resized");
                System.out.println("PASS " + channel + ": positions, nullable outputs and surface resize");
            }
        }
    }
}
