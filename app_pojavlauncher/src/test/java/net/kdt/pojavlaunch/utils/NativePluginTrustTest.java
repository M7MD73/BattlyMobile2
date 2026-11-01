package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class NativePluginTrustTest {
    private static final String SIGNER = String.join("", Collections.nCopies(64, "a"));
    @Test public void newAndUnidentifiedPluginsAreDisabled() {
        assertFalse(NativePluginTrust.isApproved(Collections.emptySet(), "example.plugin", SIGNER));
        assertFalse(NativePluginTrust.isApproved(Collections.singleton("example.plugin"), "example.plugin", SIGNER));
        assertFalse(NativePluginTrust.isApproved(Collections.emptySet(), "example.plugin", ""));
    }
    @Test public void approvalBindsPackageAndSignerButSurvivesSameSignerUpdates() {
        java.util.Set<String> approved = Collections.singleton(NativePluginTrust.key("example.plugin", SIGNER));
        assertTrue(NativePluginTrust.isApproved(approved, "example.plugin", SIGNER));
        assertFalse(NativePluginTrust.isApproved(approved, "other.plugin", SIGNER));
        assertFalse(NativePluginTrust.isApproved(approved, "example.plugin", SIGNER.replace('a', 'b')));
    }
}
