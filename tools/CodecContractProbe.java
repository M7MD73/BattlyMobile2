import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import net.kdt.patch.CodecNativeTransformer;
import net.kdt.patch.asm.*;

public final class CodecContractProbe {
    public static void main(String[] args) throws Exception {
        String oldArch = System.getProperty("os.arch");
        File nativeFile = File.createTempFile("battly-codec-", ".so");
        byte[] header = new byte[20];
        header[0]=0x7f; header[1]='E'; header[2]='L'; header[3]='F'; header[4]=2; header[5]=1; header[18]=(byte)183;
        java.nio.file.Files.write(nativeFile.toPath(), header);
        try {
            for (String codec : new String[]{"opus4j", "rnnoise4j", "speex4j", "lame4j"}) {
                String property = "battly.codec." + codec + ".path";
                String name = "de/maxhenkel/" + codec + "/LibraryLoader";
                File reference = args.length > 1 ? new File(args[1]) : new File(args[0] + "/build/" + codec + "-reference.jar");
                try (URLClassLoader loader = new URLClassLoader(new URL[]{reference.toURI().toURL()}, null)) {
                    byte[] original;
                    try (InputStream in = loader.getResourceAsStream(name + ".class")) { original = in.readAllBytes(); }
                    System.setProperty("os.arch", "aarch64");
                    System.setProperty(property, nativeFile.getAbsolutePath());
                    byte[] patched = CodecNativeTransformer.transform(loader, name, original);
                    if (patched == null || !new String(patched, StandardCharsets.ISO_8859_1).contains(nativeFile.getAbsolutePath())) throw new AssertionError(codec + " path not patched");
                    System.setProperty("os.arch", "x86");
                    if (CodecNativeTransformer.transform(loader, name, original) != null) throw new AssertionError("wrong ABI accepted");
                    System.setProperty("os.arch", "aarch64");
                    ClassLoader missing = new ClassLoader(null) {};
                    if (CodecNativeTransformer.transform(missing, name, original) != null) throw new AssertionError("missing contract accepted");
                    ClassLoader changedApi = new ClassLoader(loader) {
                        @Override public InputStream getResourceAsStream(String resource) {
                            InputStream in = loader.getResourceAsStream(resource);
                            if (in == null || !resource.endsWith(".class")) return in;
                            try (InputStream stream = in) {
                                ClassReader reader = new ClassReader(stream);
                                ClassWriter writer = new ClassWriter(reader, 0);
                                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                                    @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                                        return (a & Opcodes.ACC_NATIVE) != 0 ? null : super.visitMethod(a, n, d, s, e);
                                    }
                                }, 0);
                                return new ByteArrayInputStream(writer.toByteArray());
                            } catch (IOException ex) { throw new RuntimeException(ex); }
                        }
                    };
                    if (CodecNativeTransformer.transform(changedApi, name, original) != null) throw new AssertionError("changed JNI API accepted");
                    System.clearProperty(property);
                    if (CodecNativeTransformer.transform(loader, name, original) != null) throw new AssertionError("unconfigured library patched");
                    System.out.println("PASS " + codec + ": actual reference JNI contract, ABI guard, missing classes, fallback");
                } finally { System.clearProperty(property); }
            }
        } finally { System.setProperty("os.arch", oldArch); nativeFile.delete(); }
    }
}
