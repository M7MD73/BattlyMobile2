package net.kdt.patch;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import net.kdt.patch.asm.*;

/** Redirects extraction only when every JNI declaration matches the bundled build. */
public final class CodecNativeTransformer {
    private CodecNativeTransformer() {}

    public static byte[] transform(ClassLoader loader, String name, byte[] original) throws Exception {
        if (loader == null) return null;
        String codec = null;
        for (String candidate : new String[]{"opus4j", "rnnoise4j", "speex4j", "lame4j"}) {
            if (name.equals("de/maxhenkel/" + candidate + "/LibraryLoader")) codec = candidate;
        }
        if (codec == null) return null;
        String path = System.getProperty("battly.codec." + codec + ".path");
        if (path == null) return null;
        File library = new File(path);
        if (!library.isAbsolute() || !library.isFile()
                || !ImGuiNativeTransformer.matchesArchitecture(library, System.getProperty("os.arch", ""))) return null;
        Properties contract = new Properties();
        try (InputStream in = CodecNativeTransformer.class.getResourceAsStream("codecs/" + codec + ".properties")) {
            if (in == null) return null;
            contract.load(in);
        }
        if (contract.isEmpty()) return null;
        for (String entry : contract.stringPropertyNames()) {
            try (InputStream in = loader.getResourceAsStream(entry + ".class")) {
                if (in == null || !contract.getProperty(entry).equals(nativeContract(new ClassReader(in)))) {
                    System.err.println("[BattlyNative] Unsupported " + codec + " JNI contract; keeping its loader unchanged.");
                    return null;
                }
            }
        }
        ClassReader reader = new ClassReader(original);
        if (!name.equals(reader.getClassName())) return null;
        ClassWriter writer = new ClassWriter(reader, 0);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                       String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                          String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/System")
                                && method.equals("load") && descriptor.equals("(Ljava/lang/String;)V")) {
                            // Keep System.load in the mod's loader so its JNI symbols resolve there.
                            super.visitInsn(Opcodes.POP);
                            super.visitLdcInsn(library.getAbsolutePath());
                            changed[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : null;
    }

    public static String nativeContract(ClassReader reader) {
        Set<String> methods = new TreeSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_NATIVE) != 0) {
                    methods.add(((access & Opcodes.ACC_STATIC) != 0 ? "static:" : "instance:") + name + descriptor);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return String.join(";", methods);
    }
}
