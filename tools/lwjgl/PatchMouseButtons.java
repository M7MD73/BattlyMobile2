import java.nio.file.*;
import java.util.jar.*;
import java.io.*;
import net.kdt.patch.asm.*;

/** Repairs the bundled LWJGLX three-byte buffer without changing its input API. */
public final class PatchMouseButtons {
    private static final String OWNER = "org/lwjgl/input/GLFWInputImplementation";
    private static final String MARKER = "BATTLY_MOUSE_BUTTON_CAPACITY";

    public static void main(String[] paths) throws Exception {
        for (String path : paths) patch(Paths.get(path));
    }

    private static void patch(Path path) throws Exception {
        byte[] patched;
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry entry = jar.getJarEntry(OWNER + ".class");
            if (entry == null) throw new IOException("Missing LWJGLX input class: " + path);
            byte[] bytes;
            try (InputStream stream = jar.getInputStream(entry)) { bytes = stream.readAllBytes(); }
            ClassReader reader = new ClassReader(bytes);
            boolean[] alreadyPatched = {false};
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String name, String desc,
                                                          String signature, Object value) {
                    if (MARKER.equals(name)) alreadyPatched[0] = true;
                    return null;
                }
            }, ClassReader.SKIP_CODE);
            if (alreadyPatched[0]) { System.out.println("Already patched: " + path); return; }
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            int[] assignments = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                           String signature, String[] exceptions) {
                    MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
                    if (!"<init>".equals(name)) return visitor;
                    return new MethodVisitor(Opcodes.ASM9, visitor) {
                        @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                            if (opcode == Opcodes.PUTFIELD && OWNER.equals(owner)
                                    && "mouse_buffer".equals(name) && "[B".equals(desc)) {
                                // Preserve a larger upstream buffer; GLFW supports eight buttons.
                                super.visitInsn(Opcodes.DUP);
                                super.visitInsn(Opcodes.ARRAYLENGTH);
                                super.visitIntInsn(Opcodes.BIPUSH, 8);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf", "([BI)[B", false);
                                assignments[0]++;
                            }
                            super.visitFieldInsn(opcode, owner, name, desc);
                        }
                    };
                }
                @Override public void visitEnd() {
                    super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            MARKER, "I", null, 8).visitEnd();
                    super.visitEnd();
                }
            }, 0);
            if (assignments[0] != 1) throw new IOException("Unexpected LWJGLX constructor: " + path);
            patched = writer.toByteArray();
        }
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "mouse-patch-", ".jar");
        try {
            try (JarFile jar = new JarFile(path.toFile());
                 JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary))) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    JarEntry copy = new JarEntry(entry.getName());
                    copy.setTime(entry.getTime());
                    output.putNextEntry(copy);
                    if (entry.getName().equals(OWNER + ".class")) output.write(patched);
                    else try (InputStream input = jar.getInputStream(entry)) { input.transferTo(output); }
                    output.closeEntry();
                }
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        System.out.println("Patched: " + path);
    }
}
