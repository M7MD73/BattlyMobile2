package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MobileGluesVersionPolicyTest {
    @Test
    public void inheritedProfilesUseTheBaseMinecraftVersion() {
        assertEquals("26.3", MobileGluesVersionPolicy.resolveMinecraftVersion(
                "fabric-loader-0.19.5-26.3", "26.3"));
        assertEquals("1.20.1", MobileGluesVersionPolicy.resolveMinecraftVersion(
                "1.20.1-forge-47.4.23", "1.20.1"));
    }

    @Test
    public void vanillaProfilesKeepTheirOwnVersion() {
        assertEquals("26.3", MobileGluesVersionPolicy.resolveMinecraftVersion("26.3", null));
        assertEquals("26.3-snapshot-7",
                MobileGluesVersionPolicy.resolveMinecraftVersion("26.3-snapshot-7", " "));
    }

    @Test
    public void workaroundStartsAtSnapshotThreeAndCoversTheRelease() {
        assertFalse(MobileGluesVersionPolicy.requiresShaderErrorWorkaround("26.2"));
        assertFalse(MobileGluesVersionPolicy.requiresShaderErrorWorkaround("26.3-snapshot-2"));
        assertTrue(MobileGluesVersionPolicy.requiresShaderErrorWorkaround("26.3-snapshot-3"));
        assertTrue(MobileGluesVersionPolicy.requiresShaderErrorWorkaround("26.3-snapshot-9"));
        assertTrue(MobileGluesVersionPolicy.requiresShaderErrorWorkaround("26.3"));
        assertTrue(MobileGluesVersionPolicy.requiresShaderErrorWorkaround("26.4"));
    }
}
