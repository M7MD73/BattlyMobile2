import java.io.*;
import java.util.*;
import java.util.jar.*;
import net.kdt.patch.CodecNativeTransformer;
import net.kdt.patch.asm.ClassReader;

public final class GenerateCodecContract {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (JarFile jar = new JarFile(args[0])) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("de/maxhenkel/" + args[1] + "/")
                        || !entry.getName().endsWith(".class")) continue;
                try (InputStream in = jar.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(in);
                    String contract = CodecNativeTransformer.nativeContract(reader);
                    if (!contract.isEmpty()) properties.setProperty(reader.getClassName(), contract);
                }
            }
        }
        if (properties.isEmpty()) throw new IOException("Missing JNI declarations");
        File output = new File(args[2]);
        output.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(output)) {
            properties.store(out, "Bundled Android JNI contract for " + args[1]);
        }
    }
}
