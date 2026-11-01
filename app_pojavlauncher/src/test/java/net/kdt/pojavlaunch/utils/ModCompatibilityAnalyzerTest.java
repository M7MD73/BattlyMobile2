package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModCompatibilityAnalyzerTest {
    @org.junit.Test
    public void desktopNativeFailuresGetActionableDiagnostics() {
        org.junit.Assert.assertTrue(ModCompatibilityAnalyzer.analyze(
                "net.hnt3r.utils.HunterProtection kernel32 UnsatisfiedLinkError").summary.contains("Windows"));
        org.junit.Assert.assertTrue(ModCompatibilityAnalyzer.analyze(
                "de.maxhenkel.voicechat libopus4j libm.so.6 UnsatisfiedLinkError").summary.contains("desktop"));
        org.junit.Assert.assertTrue(ModCompatibilityAnalyzer.analyze(
                "morecosmetics java.awt.Component.initIDs UnsatisfiedLinkError").summary.contains("debug console"));
        org.junit.Assert.assertFalse(ModCompatibilityAnalyzer.analyze(
                "Renderer: opengles3_krypton").detected);
    }

    @Test
    public void identifiesKryptonE4mcRedirectConflictInsteadOfControlify() {
        String log = "Loading Minecraft 1.21.1 with Fabric Loader 0.19.3\n"
                + "\t- controlify 3.0.0+lts+1.21.1-fabric\n"
                + "\t- e4mc 6.1.1\n"
                + "\t- krypton 0.2.8\n"
                + "@Redirect conflict. Skipping krypton.mixins.json from mod krypton, "
                + "already redirected by e4mc.mixins.json from mod e4mc\n"
                + "Could not execute entrypoint provided by controlify\n";

        ModCompatibilityAnalyzer.Analysis result = ModCompatibilityAnalyzer.analyze(log);

        assertTrue(result.detected);
        assertEquals("Fabric", result.loader);
        assertEquals("krypton", result.issues.get(0).modId);
        assertTrue(result.summary.contains("Krypton and e4mc"));
    }

    @Test
    public void identifiesOptifineEmbeddiumRendererConflict() {
        String log = "OptiFineTransformationService.initialize\n"
                + "Found mod file embeddium-0.3.31+mc1.20.1.jar\n"
                + "InjectionError: Critical injection failure in embeddium.mixins.json";

        ModCompatibilityAnalyzer.Analysis result = ModCompatibilityAnalyzer.analyze(log);

        assertTrue(result.detected);
        assertTrue(result.summary.contains("OptiFine and Embeddium"));
        assertEquals("Forge", result.loader);
    }
    @Test
    public void identifiesMixinModsAndHidesSecondaryAwtFailure() {
        String log = "Loading Minecraft 26.2 with Fabric Loader 0.19.3\n"
                + "\t- sodium 0.9.1+mc26.1.2\n"
                + "Mixin apply for mod sodium failed: InvalidInjectionException\n"
                + "java.lang.UnsatisfiedLinkError: 'void java.awt.Insets.initIDs()'";

        ModCompatibilityAnalyzer.Analysis result = ModCompatibilityAnalyzer.analyze(log);

        assertTrue(result.detected);
        assertEquals("sodium", result.issues.get(0).modId);
        assertEquals("0.9.1+mc26.1.2", result.issues.get(0).installedVersion);
        assertFalse(result.primaryExcerpt.contains("Insets.initIDs"));
    }

    @Test
    public void explainsOutdatedQuiltLoaderForJava25Bytecode() {
        String log = "Loading Minecraft 26.1.2 with Quilt Loader 0.24.0\n"
                + "IllegalArgumentException: Unsupported class file major version 69";

        ModCompatibilityAnalyzer.Analysis result = ModCompatibilityAnalyzer.analyze(log);

        assertTrue(result.detected);
        assertEquals("Quilt", result.loader);
        assertTrue(result.summary.contains("Quilt 0.24.0"));
        assertTrue(result.summary.contains("Java 25"));
        assertTrue(result.solution.contains("newer Quilt"));
    }
}
