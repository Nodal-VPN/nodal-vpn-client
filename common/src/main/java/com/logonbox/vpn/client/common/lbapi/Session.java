package com.logonbox.vpn.client.common.lbapi;

import jakarta.json.JsonObject;

public record Session(String id, String csrfToken, Principal currentPrincipal) {

    public static Session of(JsonObject obj) {
        return new Session(
            obj.getString("id", null),
            obj.getString("csrfToken", null),
            Principal.of(obj.get("currentPrincipal").asJsonObject())
        );
    }
}
