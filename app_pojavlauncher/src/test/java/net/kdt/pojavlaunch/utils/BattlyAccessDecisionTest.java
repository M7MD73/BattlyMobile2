package net.kdt.pojavlaunch.utils;

import net.kdt.pojavlaunch.value.MinecraftAccount;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.kdt.pojavlaunch.utils.BattlyAccessDecision.State.*;

public class BattlyAccessDecisionTest {
    @Test public void acceptsPushAndRejectedHandshakeButNotConnectionErrors() throws Exception {
        JSONObject ban = new JSONObject().put("code", "BATTLY_BANNED").put("banned", true)
                .put("scope", "account").put("message", "Fixture");
        assertEquals(BANNED, BattlyAccessDecision.fromRealtime(ban).state);
        assertEquals(BANNED, BattlyAccessDecision.fromRealtime(new JSONObject().put("data", ban)).state);
        assertEquals(UNAVAILABLE, BattlyAccessDecision.fromRealtime(new Exception("timeout")).state);
        assertEquals(UNAVAILABLE, BattlyAccessDecision.fromRealtime("BATTLY_BANNED").state);
        assertEquals(UNAVAILABLE, BattlyAccessDecision.fromRealtime(new JSONObject().put("message", "offline")).state);
        ban.put("banned", "true");
        assertEquals(UNAVAILABLE, BattlyAccessDecision.fromRealtime(ban).state);
    }
    @Test public void explicitAccountAndIpBansAreRecognized() throws Exception {
        for (String scope : new String[]{"ip", "account", "device"}) {
            BattlyAccessDecision result = BattlyAccessDecision.parse(403,
                    new JSONObject().put("code", "BATTLY_BANNED").put("banned", true)
                            .put("scope", scope).put("message", "Fixture"));
            assertEquals(BANNED, result.state);
            assertEquals(scope, result.scope);
            assertEquals("Fixture", result.reason);
        }
    }
    @Test public void maintenanceAndErrorsDoNotCreateBans() throws Exception {
        assertEquals(UNAVAILABLE, BattlyAccessDecision.parse(200,
                new JSONObject().put("maintenance", true).put("maintenance_message", "Maintenance")).state);
        assertEquals(UNAVAILABLE, BattlyAccessDecision.parse(403, new JSONObject()).state);
        assertEquals(UNAVAILABLE, BattlyAccessDecision.parse(503, null).state);
        assertEquals(UNAVAILABLE, BattlyAccessDecision.parse(429, new JSONObject()).state);
        assertEquals(UNAVAILABLE, BattlyAccessDecision.parse(200,
                new JSONObject().put("allowed", "true").put("banned", false)).state);
    }
    @Test public void successAndExpiredSessionRemainDistinct() throws Exception {
        assertFalse(BattlyAccessDecision.parse(200, new JSONObject().put("allowed", true).put("banned", false)).deviceChecked);
        assertTrue(BattlyAccessDecision.parse(200, new JSONObject().put("allowed", true).put("banned", false)
                .put("deviceChecked", true)).deviceChecked);
        assertEquals(ALLOWED, BattlyAccessDecision.parse(200,
                new JSONObject().put("allowed", true).put("banned", false)).state);
        assertEquals(LOGIN_REQUIRED, BattlyAccessDecision.parse(401,
                new JSONObject().put("code", "INVALID_TOKEN")).state);
    }
    @Test public void neverSendsMicrosoftOrLocalCredentialsToBattly() {
        MinecraftAccount account = new MinecraftAccount();
        assertEquals("", BattlyAccessManager.tokenFor(account));
        account.accessToken = "microsoft-secret";
        account.isMicrosoft = true;
        assertEquals("", BattlyAccessManager.tokenFor(account));
        account.isMicrosoft = false;
        assertEquals("microsoft-secret", BattlyAccessManager.tokenFor(account));
        account.accessToken = null;
        assertEquals("", BattlyAccessManager.tokenFor(account));
        assertEquals(BattlyAccessManager.accountKey("Player"), BattlyAccessManager.accountKey("player"));
    }
}
