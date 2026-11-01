import imgui.moulberry92.ImGui;
import imgui.moulberry92.ImGuiIO;
import imgui.moulberry92.type.ImInt;

/** Run with the unmodified Axiom JAR and Battly's agent on the Android JVM. */
public final class ImGuiSmoke {
    public static void main(String[] args) {
        ImGui.init();
        System.out.println("ImGui initialized: " + ImGui.getVersion());
        ImGui.createContext();
        try {
            ImGuiIO io = ImGui.getIO();
            io.setDisplaySize(1280, 720);
            io.setDeltaTime(1f / 60f);
            io.setIniFilename(null);
            ImInt width = new ImInt();
            ImInt height = new ImInt();
            if (io.getFonts().getTexDataAsRGBA32(width, height).remaining() == 0) {
                throw new AssertionError("Empty font atlas");
            }
            io.getFonts().setTexID(1);
            imgui.moulberry92.extension.texteditor.TextEditor editor =
                    new imgui.moulberry92.extension.texteditor.TextEditor();
            editor.setText("// Axiom script brush JNI\nreturn 42;");
            if (!editor.getText().contains("return 42;")) throw new AssertionError("Text editor JNI failed");
            int vertices = 0;
            for (int frame = 0; frame < 120; frame++) {
                ImGui.newFrame();
                ImGui.begin("Battly Axiom native test");
                ImGui.text("Android ImGui JNI and font atlas");
                editor.render("Script brush editor");
                ImGui.end();
                ImGui.render();
                vertices += ImGui.getDrawData().getTotalVtxCount();
            }
            if (vertices <= 0) throw new AssertionError("No geometry generated");
            editor.destroy();
            System.out.println("PASS: 120 frames, vertices=" + vertices + ", font=" + width.get() + "x" + height.get());
        } finally { ImGui.destroyContext(); }
    }
}
