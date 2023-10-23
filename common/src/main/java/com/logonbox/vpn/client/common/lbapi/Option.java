package com.logonbox.vpn.client.common.lbapi;

import jakarta.json.JsonObject;

public record Option(
        String name,
        String value,
        boolean selected,
        boolean isNameResourceKey,
        String description,
        boolean isDescriptionResourceKey) {	
    
    public static Option of(JsonObject obj) {
        return new Option(
            obj.getString("name", null),
            obj.getString("value", null),
            obj.getBoolean("selected", false),
            obj.getBoolean("isNameResourceKey", false),
            obj.getString("description", null),
            obj.getBoolean("isDescriptionResourceKey", false)
        );
    }
}
