package net.kdt.pojavlaunch.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class OAuthCallbackPolicy {
    public static final long TIMEOUT_MS = 120000;
    private OAuthCallbackPolicy() { }

    public static boolean matches(String expectedProvider, String expectedNonce, long started,
                                  String provider, String nonce, long now) {
        return expectedProvider != null && expectedProvider.equals(provider)
                && expectedNonce != null && !expectedNonce.isEmpty() && nonce != null
                && started >= 0 && now >= started && now - started <= TIMEOUT_MS
                && MessageDigest.isEqual(expectedNonce.getBytes(StandardCharsets.UTF_8),
                        nonce.getBytes(StandardCharsets.UTF_8));
    }
}
