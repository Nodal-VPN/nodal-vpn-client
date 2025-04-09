/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
