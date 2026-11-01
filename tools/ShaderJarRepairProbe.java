import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import net.kdt.patch.asm.*;

/** Runs the actual repair class with only Android rename/logging replaced on the host. */
public final class ShaderJarRepairProbe {
    public static void rename(String from, String to) throws IOException {
        Files.move(Paths.get(from), Paths.get(to), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static byte[] stub(String name) {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
        boolean os = name.equals("android.system.Os");
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                os ? "rename" : "appendToLog", os ? "(Ljava/lang/String;Ljava/lang/String;)V" : "(Ljava/lang/String;)V", null, null);
        m.visitCode();
        if (os) {
            m.visitVarInsn(Opcodes.ALOAD, 0); m.visitVarInsn(Opcodes.ALOAD, 1);
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "ShaderJarRepairProbe", "rename", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        }
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(2, 2); m.visitEnd(); w.visitEnd();
        return w.toByteArray();
    }

    private static void fixture(Path file, String json) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            for (String entry : new String[]{"assets/minecraft/shaders/post/entity_outline.json", "assets/custom/keep.txt", "META-INF/KEEP.SF"}) {
                out.putNextEntry(new JarEntry(entry));
                out.write((entry.endsWith(".json") ? json : "preserve").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        final String target = "net.kdt.pojavlaunch.utils.VanillaPostShaderCompat";
        ClassLoader loader = new ClassLoader(ShaderJarRepairProbe.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(target) && !name.equals("net.kdt.pojavlaunch.Logger") && !name.equals("android.system.Os")) return super.loadClass(name, resolve);
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try {
                        byte[] bytes = name.equals(target)
                                ? Files.readAllBytes(Paths.get(args[0], name.replace('.', '/') + ".class")) : stub(name);
                        c = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException ex) { throw new ClassNotFoundException(name, ex); }
                }
                if (resolve) resolveClass(c);
                return c;
            }
        };
        Method repair = loader.loadClass(target).getDeclaredMethod("repairShaderJar", File.class, boolean.class);
        repair.setAccessible(true);
        Path dir = Files.createTempDirectory("battly-shader-repair-");
        try {
            Path broken = dir.resolve("broken.jar");
            fixture(broken, "{\"battly_android_disabled\":true,\"targets\":[],\"passes\":[]}");
            repair.invoke(null, broken.toFile(), true);
            try (JarFile jar = new JarFile(broken.toFile())) {
                String json = new String(jar.getInputStream(jar.getJarEntry("assets/minecraft/shaders/post/entity_outline.json")).readAllBytes(), StandardCharsets.UTF_8);
                if (!json.contains("\"vertex\"") || !json.contains("\"fragment\"") || json.contains("battly_android_disabled")) throw new AssertionError("poison retained");
                if (!new String(jar.getInputStream(jar.getJarEntry("assets/custom/keep.txt")).readAllBytes(), StandardCharsets.UTF_8).equals("preserve")) throw new AssertionError("unrelated entry changed");
            }
            byte[] once = Files.readAllBytes(broken);
            repair.invoke(null, broken.toFile(), true);
            if (!Arrays.equals(once, Files.readAllBytes(broken))) throw new AssertionError("not idempotent");
            Path custom = dir.resolve("custom.jar");
            fixture(custom, "{\"vertex\":\"custom:v\",\"fragment\":\"custom:f\"}");
            byte[] before = Files.readAllBytes(custom);
            repair.invoke(null, custom.toFile(), true);
            if (!Arrays.equals(before, Files.readAllBytes(custom))) throw new AssertionError("custom program/signature changed");
            System.out.println("PASS: actual JAR repair without layout marker; resources preserved, idempotent, custom signed JAR untouched");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.list(dir)) { for (Path p : paths.toArray(Path[]::new)) Files.delete(p); }
            Files.delete(dir);
        }
    }
}
