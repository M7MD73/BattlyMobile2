package net.kdt.pojavlaunch.battlyworlds;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A restriction is defined by its scope, not by whether its reason is nonempty. */
final class BattlyWorldsRestriction {
    static JsonObject find(JsonObject response, String scope) {
        if (response == null || !response.has("restrictions")
                || !response.get("restrictions").isJsonArray()) return null;
        for (JsonElement value : response.getAsJsonArray("restrictions")) {
            if (!value.isJsonObject()) continue;
            JsonObject row = value.getAsJsonObject();
            if (!row.has("scopes") || !row.get("scopes").isJsonArray()) continue;
            for (JsonElement item : row.getAsJsonArray("scopes")) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) continue;
                String current = item.getAsString();
                if (current.equals("worlds") || current.equals(scope)) return row;
            }
        }
        return null;
    }

    private BattlyWorldsRestriction() {}
}
