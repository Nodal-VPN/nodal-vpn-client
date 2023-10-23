package com.logonbox.vpn.client.common.lbapi;

import jakarta.json.JsonObject;

public record Realm(
        Long id, 
        String name,
        boolean system,
        String resourceCategory) {

    public static Realm of(JsonObject obj) {
        return new Realm(
            obj.getJsonNumber("id").longValue(),
            obj.getString("name", null),
            obj.getBoolean("system", false),
            obj.getString("resourceCategory", null)
        );
    }

}
