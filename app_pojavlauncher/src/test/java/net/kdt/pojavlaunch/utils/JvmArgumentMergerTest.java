package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JvmArgumentMergerTest {
    @Test
    public void headlessPropertyPrecedesMainClassAndDoesNotEatApplicationArguments() {
        assertEquals(Arrays.asList("-Djava.awt.headless=true", "-cp", "game.jar", "Main", "--username", "test"),
                JvmArgumentMerger.withLeadingJvmOption(Arrays.asList(
                        "-Djava.awt.headless=false", "-cp", "game.jar", "Main", "--username", "test"),
                        "-Djava.awt.headless=true"));
        assertEquals(Arrays.asList("-Djava.awt.headless=true", "Main", "-Djava.awt.headless=game-value"),
                JvmArgumentMerger.withLeadingJvmOption(Arrays.asList("Main", "-Djava.awt.headless=game-value"),
                        "-Djava.awt.headless=true"));
    }

    @Test
    public void customSystemPropertyReplacesLaunchProperty() {
        List<String> result = JvmArgumentMerger.removeOverriddenJvmOptions(
                Arrays.asList("-Dexample.mode=launch", "-Dkeep=true", "-cp", "game.jar", "Main"),
                Arrays.asList("-Dexample.mode=custom"));

        assertEquals(Arrays.asList("-Dkeep=true", "-cp", "game.jar", "Main"), result);
    }

    @Test
    public void similarlyNamedPropertiesRemainIndependent() {
        List<String> result = JvmArgumentMerger.removeOverriddenJvmOptions(
                Arrays.asList("-Djava.home=/runtime", "-Djava.homebrew=enabled"),
                Arrays.asList("-Djava.homebrew=custom"));

        assertEquals(Arrays.asList("-Djava.home=/runtime"), result);
    }

    @Test
    public void oppositeBooleanFlagReplacesTheOriginalFlag() {
        List<String> result = JvmArgumentMerger.removeOverriddenJvmOptions(
                Arrays.asList("-XX:+UseStringDeduplication", "-XX:+Keep"),
                Arrays.asList("-XX:-UseStringDeduplication"));

        assertEquals(Arrays.asList("-XX:+Keep"), result);
    }

    @Test
    public void differentModuleOpensAndAgentsCanCoexist() {
        List<String> result = JvmArgumentMerger.removeOverriddenJvmOptions(
                Arrays.asList(
                        "--add-opens=java.base/java.lang=launcher.module",
                        "--add-opens=java.base/java.util=ALL-UNNAMED",
                        "-javaagent:/launcher/first.jar=old",
                        "-javaagent:/launcher/second.jar"),
                Arrays.asList(
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "-javaagent:/launcher/first.jar=new"));

        assertEquals(Arrays.asList(
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "-javaagent:/launcher/second.jar"), result);
    }

    @Test
    public void applicationArgumentsAfterMainClassAreNeverRemoved() {
        List<String> result = JvmArgumentMerger.removeOverriddenJvmOptions(
                Arrays.asList("-Dmode=launch", "-cp", "game.jar", "Main", "-Dmode=game-option"),
                Arrays.asList("-Dmode=custom"));

        assertEquals(Arrays.asList("-cp", "game.jar", "Main", "-Dmode=game-option"), result);
    }

    @Test
    public void launchOptionsAfterClasspathAreStillReplaced() {
        List<String> result = JvmArgumentMerger.removeOverriddenJvmOptions(
                Arrays.asList(
                        "-cp", "game.jar",
                        "-Dfml.ignoreInvalidMinecraftCertificates=true",
                        "net.minecraft.client.main.Main",
                        "--username", "Player"),
                Arrays.asList("-Dfml.ignoreInvalidMinecraftCertificates=false"));

        assertEquals(Arrays.asList(
                "-cp", "game.jar",
                "net.minecraft.client.main.Main",
                "--username", "Player"), result);
    }

    @Test
    public void exactOptionLookupDoesNotUsePrefixMatching() {
        List<String> existing = Arrays.asList("-Djava.homebrew=custom");

        assertEquals(false, JvmArgumentMerger.containsSameOption(existing, "-Djava.home=/runtime"));
        assertEquals(true, JvmArgumentMerger.containsSameOption(existing, "-Djava.homebrew=default"));
    }

    @Test
    public void customCodeCacheSizeSuppressesTheLauncherDefault() {
        List<String> existing = Arrays.asList("-XX:ReservedCodeCacheSize=128M");

        assertEquals(true, JvmArgumentMerger.containsSameOption(
                existing, "-XX:ReservedCodeCacheSize=48M"));
    }
}
