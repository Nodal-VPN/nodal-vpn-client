package com.logonbox.vpn.client.common.lbapi;

import jakarta.json.JsonObject;

public record Peer(Principal principal) {

    public static Peer of(JsonObject obj) {
        return new Peer(Principal.of(obj.get("principal").asJsonObject()));
    }
	
	
}
