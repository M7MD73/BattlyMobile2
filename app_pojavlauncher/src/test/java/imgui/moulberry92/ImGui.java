package imgui.moulberry92;

/** A loader fixture, not a replacement for the mod's ImGui classes. */
public class ImGui {
    private static String tryLoadFromClasspath(String name) { return "desktop/" + name; }
    public static String loadPath() { return tryLoadFromClasspath("libimgui-moulberry92-java64.so"); }
    public static int untouched() { return 92; }
}
