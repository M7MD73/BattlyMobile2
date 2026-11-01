package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class OAuthCallbackPolicyTest {
    @Test public void requiresPendingMatchingUnexpiredTransaction() {
        assertTrue(OAuthCallbackPolicy.matches("discord", "nonce", 100, "discord", "nonce", 200));
        assertFalse(OAuthCallbackPolicy.matches("discord", "", 100, "discord", "", 200));
        assertFalse(OAuthCallbackPolicy.matches("discord", "nonce", 100, "discord", "wrong", 200));
        assertFalse(OAuthCallbackPolicy.matches("discord", "nonce", 100, "other", "nonce", 200));
        assertFalse(OAuthCallbackPolicy.matches("discord", "nonce", 100, "discord", "nonce", 120101));
        assertFalse(OAuthCallbackPolicy.matches("discord", "nonce", 100, "discord", "nonce", 50));
    }
}
