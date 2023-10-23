package com.logonbox.vpn.client.common.lbapi;

import jakarta.json.JsonObject;

public record Branding(boolean success,
        String message,
        BrandingInfo resource,
        String logo) {
    
    public Branding logo(String newLogo) {
        return new Branding(
                success(),
                message(),
                resource(),
                newLogo
        );
    }

    public static Branding of(JsonObject obj) {
        return new Branding(
            obj.getBoolean("success", true),
            obj.getString("message", null),
            obj.containsKey("resource") ?  BrandingInfo.of(obj.get("resource").asJsonObject()) : null,
            obj.getString("logo", null)
        );
    }
}
