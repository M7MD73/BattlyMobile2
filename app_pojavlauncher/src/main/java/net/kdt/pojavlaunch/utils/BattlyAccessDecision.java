package net.kdt.pojavlaunch.utils;

import org.json.JSONObject;

/** Maintenance, invalid credentials and outages must never become persistent bans. */
public final class BattlyAccessDecision {
    public enum State { ALLOWED, BANNED, LOGIN_REQUIRED, UNAVAILABLE }
    public final State state;
    public final String scope;
    public final String reason;
    public final boolean deviceChecked;

    BattlyAccessDecision(State state, String scope, String reason) {
        this(state, scope, reason, false);
    }

    private BattlyAccessDecision(State state, String scope, String reason, boolean deviceChecked) {
        this.state = state;
        this.scope = scope;
        this.reason = reason;
        this.deviceChecked = deviceChecked;
    }

    public static BattlyAccessDecision fromRealtime(Object event) {
        if (!(event instanceof JSONObject)) return parse(0, null);
        JSONObject body = (JSONObject) event;
        JSONObject errorData = body.optJSONObject("data");
        return parse(403, errorData == null ? body : errorData);
    }

    public static BattlyAccessDecision parse(int status, JSONObject body) {
        if (body != null && (status == 403 || status == 200)
                && "BATTLY_BANNED".equals(body.optString("code")) && Boolean.TRUE.equals(body.opt("banned"))) {
            String scope = body.optString("scope", "");
            if ("ip".equals(scope) || "account".equals(scope) || "device".equals(scope)) {
                String reason = body.optString("message", "");
                return new BattlyAccessDecision(State.BANNED, scope,
                        reason.substring(0, Math.min(1000, reason.length())));
            }
        }
        if (status == 200 && body != null && Boolean.TRUE.equals(body.opt("allowed"))
                && Boolean.FALSE.equals(body.opt("banned"))) {
            return new BattlyAccessDecision(State.ALLOWED, "", "", Boolean.TRUE.equals(body.opt("deviceChecked")));
        }
        if (status == 401 && body != null && "INVALID_TOKEN".equals(body.optString("code"))) {
            return new BattlyAccessDecision(State.LOGIN_REQUIRED, "", "");
        }
        return new BattlyAccessDecision(State.UNAVAILABLE, "", "");
    }
}
