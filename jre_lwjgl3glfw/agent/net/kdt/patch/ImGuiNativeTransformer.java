package net.kdt.patch;

import java.io.File;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.Locale;
import java.util.Properties;
import net.kdt.patch.asm.ClassReader;
import net.kdt.patch.asm.ClassVisitor;
import net.kdt.patch.asm.ClassWriter;
import net.kdt.patch.asm.MethodVisitor;
import net.kdt.patch.asm.Opcodes;

/** Keeps Axiom's Java/JNI API intact, replacing only its desktop extraction path. */
public final class ImGuiNativeTransformer {
    private static final String VERSION = "1.92-moulberry.2";

    private ImGuiNativeTransformer() { }

    public static byte[] transform(ClassLoader loader, byte[] original) throws Exception {
        String path = System.getProperty("battly.imgui.moulberry92.path");
        if (path == null || loader == null) return null;
        Properties properties = new Properties();
        try (InputStream input = loader.getResourceAsStream("imgui/moulberry92/imgui-java.properties")) {
            if (input == null) return null;
            properties.load(input);
        }
        if (!VERSION.equals(properties.getProperty("imgui.java.version"))) {
            System.err.println("[BattlyNative] ImGui Moulberry version is not supported by the bundled native; leaving it unchanged.");
            return null;
        }
        File library = new File(path);
        if (!library.isAbsolute() || !library.isFile()
                || !matchesArchitecture(library, System.getProperty("os.arch", ""))) {
            System.err.println("[BattlyNative] ImGui Moulberry Android library is missing or has the wrong ABI.");
            return null;
        }
        ClassReader reader = new ClassReader(original);
        if (!"imgui/moulberry92/ImGui".equals(reader.getClassName())) return null;
        ClassWriter writer = new ClassWriter(reader, 0);
        final boolean[] patched = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"tryLoadFromClasspath".equals(name)
                        || !"(Ljava/lang/String;)Ljava/lang/String;".equals(descriptor)
                        || (access & Opcodes.ACC_STATIC) == 0) return method;
                // System.load remains in ImGui's own initializer and therefore its class loader.
                method.visitCode();
                method.visitLdcInsn(library.getAbsolutePath());
                method.visitInsn(Opcodes.ARETURN);
                method.visitMaxs(1, 1);
                method.visitEnd();
                patched[0] = true;
                return null;
            }
        }, 0);
        if (!patched[0]) return null;
        System.out.println("[BattlyNative] ImGui " + VERSION + " uses the Android native for "
                + System.getProperty("os.arch"));
        return writer.toByteArray();
    }

    public static boolean matchesArchitecture(File library, String architecture) throws Exception {
        String arch = architecture.toLowerCase(Locale.ROOT);
        int machine;
        int elfClass;
        switch (arch) {
            case "aarch64": case "arm64": machine = 183; elfClass = 2; break;
            case "arm": case "arm32": case "armv7": case "armv7l": machine = 40; elfClass = 1; break;
            case "amd64": case "x86_64": machine = 62; elfClass = 2; break;
            case "x86": case "i386": case "i686": machine = 3; elfClass = 1; break;
            default: return false;
        }
        try (DataInputStream input = new DataInputStream(new FileInputStream(library))) {
            byte[] header = new byte[20];
            input.readFully(header);
            return header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F'
                    && header[4] == elfClass && header[5] == 1
                    && ((header[18] & 255) | ((header[19] & 255) << 8)) == machine;
        }
    }
}
