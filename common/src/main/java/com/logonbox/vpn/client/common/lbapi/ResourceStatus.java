package com.logonbox.vpn.client.common.lbapi;

import jakarta.json.JsonObject;

public record ResourceStatus(boolean success, String message) {
    public static ResourceStatus of(JsonObject obj) {
        return new ResourceStatus(
            obj.getBoolean("success", true),
            obj.getString("message", null)
        );
    }

}
