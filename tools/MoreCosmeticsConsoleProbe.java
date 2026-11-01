import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/** Exercises MoreCosmetics 1.3's real static debug-console logging in headless mode. */
public final class MoreCosmeticsConsoleProbe {
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getProperty("java.awt.headless"))
                || !java.awt.GraphicsEnvironment.isHeadless()) throw new AssertionError("Headless flag not applied by JVM");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> console = loader.loadClass("com.cosmeticsmod.morecosmetics.utils.debug.DebugConsole");
            Class<?> state = loader.loadClass("com.cosmeticsmod.morecosmetics.utils.debug.EnumDebugState");
            Object log = state.getField("LOG").get(null);
            console.getMethod("print", String.class, state).invoke(null, "Battly headless regression", log);
            System.out.println("PASS: MoreCosmetics 1.3 DebugConsole.print initializes without opening a desktop window");
        }
    }
}
