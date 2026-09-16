package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JavaAgentValidatorTest {
    private static final String AGENT_CLASS = "net/kdt/patch/LwjglPatchAgent.class";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsAgentWithMatchingPremainAndRuntime() throws Exception {
        File agent = createAgent("net.kdt.patch.LwjglPatchAgent", AGENT_CLASS, 61);

        assertTrue(JavaAgentValidator.isUsable(agent, 17, AGENT_CLASS));
        assertTrue(JavaAgentValidator.isUsable(agent, 25, AGENT_CLASS));
    }

    @Test
    public void rejectsMissingAgentClass() throws Exception {
        File agent = createAgent("net.kdt.patch.LwjglPatchAgent", "other/Agent.class", 61);

        assertFalse(JavaAgentValidator.isUsable(agent, 25, AGENT_CLASS));
    }

    @Test
    public void rejectsWrongPremainClass() throws Exception {
        File agent = createAgent("other.Agent", AGENT_CLASS, 61);

        assertFalse(JavaAgentValidator.isUsable(agent, 25, AGENT_CLASS));
    }

    @Test
    public void rejectsAgentCompiledForNewerJava() throws Exception {
        File agent = createAgent("net.kdt.patch.LwjglPatchAgent", AGENT_CLASS, 69);

        assertFalse(JavaAgentValidator.isUsable(agent, 21, AGENT_CLASS));
    }

    @Test
    public void rejectsCorruptJar() throws Exception {
        File agent = temporaryFolder.newFile("corrupt-agent.jar");
        try (FileOutputStream output = new FileOutputStream(agent)) {
            output.write(new byte[] {1, 2, 3, 4});
        }

        assertFalse(JavaAgentValidator.isUsable(agent, 25, AGENT_CLASS));
    }

    private File createAgent(String premainClass, String classEntry, int classMajor) throws Exception {
        File outputFile = temporaryFolder.newFile("agent-" + System.nanoTime() + ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Premain-Class", premainClass);
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(outputFile), manifest)) {
            output.putNextEntry(new JarEntry(classEntry));
            output.write(new byte[] {
                    (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe,
                    0, 0, (byte) (classMajor >>> 8), (byte) classMajor
            });
            output.closeEntry();
        }
        return outputFile;
    }
}
