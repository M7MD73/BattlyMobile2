package net.kdt.pojavlaunch.battlyworlds;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class BattlyWorldsRestrictionTest {
    private JsonObject response(String rows) {
        return JsonParser.parseString("{\"restrictions\":" + rows + "}").getAsJsonObject();
    }

    @Test public void emptyReasonStillBlocksAndVoiceDoesNotBanTheRoom() {
        JsonObject voice = response("[{\"scopes\":[\"voice\"],\"reason\":\"\"}]");
        assertNotNull(BattlyWorldsRestriction.find(voice, "voice"));
        assertNull(BattlyWorldsRestriction.find(voice, "join"));
        assertNull(BattlyWorldsRestriction.find(voice, "host"));
        JsonObject global = response("[{\"scopes\":[\"worlds\"]}]");
        for (String scope : new String[]{"host", "join", "voice", "public", "invite"}) {
            assertNotNull(BattlyWorldsRestriction.find(global, scope));
        }
    }

    @Test public void malformedScopesDoNotCrashOrInventASanction() {
        assertNull(BattlyWorldsRestriction.find(response("[null,0,{\"scopes\":[{},null,17]}]"), "join"));
        assertNull(BattlyWorldsRestriction.find(new JsonObject(), "voice"));
        assertNull(BattlyWorldsRestriction.find(null, "host"));
        assertNotNull(BattlyWorldsRestriction.find(response("[{\"scopes\":[null,\"join\"]}]"), "join"));
    }
}
