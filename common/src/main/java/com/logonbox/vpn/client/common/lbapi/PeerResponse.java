package com.logonbox.vpn.client.common.lbapi;

import jakarta.json.JsonObject;

public record PeerResponse(boolean success, String message, Peer resource, String content,
        String hostname, Session session, String remoteHostname, String deviceUUID) {

    public static PeerResponse of(JsonObject obj) {
        return new PeerResponse(
            obj.getBoolean("success", true),
            obj.getString("message", null),
            obj.containsKey("resource") ?  Peer.of(obj.get("resource").asJsonObject()) : null,
            obj.getString("content", null),
            obj.getString("hostname", null),
            Session.of(obj.get("session").asJsonObject()),
            obj.getString("remoteHostname", null),
            obj.getString("deviceUUID", null)
            
        );
    }

}
