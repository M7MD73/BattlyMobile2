package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class BattlyDeviceIdentityTest {
    @Test public void protectsAndNormalizesTheIdentifier() {
        String value = BattlyDeviceIdentity.hashAndroidId("1234ABCDEF567890");
        assertTrue(value.matches("v1:[a-f0-9]{64}"));
        assertFalse(value.contains("1234abcdef567890"));
        assertEquals(value, BattlyDeviceIdentity.hashAndroidId("1234abcdef567890"));
        assertNotEquals(value, BattlyDeviceIdentity.hashAndroidId("2234abcdef567890"));
        assertEquals(BattlyDeviceIdentity.hashAndroidId("1"), BattlyDeviceIdentity.hashAndroidId("0000000000000001"));
    }
    @Test public void neverRegistersMissingInvalidOrKnownSharedIds() {
        for (String value : new String[]{null, "", "0", "0000000000000000", "9774d56d682e549c", "not-a-device", "0123456789abcdef0"}) {
            assertEquals("", BattlyDeviceIdentity.hashAndroidId(value));
        }
    }
}
