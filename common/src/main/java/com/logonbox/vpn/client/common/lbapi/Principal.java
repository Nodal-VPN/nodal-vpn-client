/**
 * Copyright 2003-2020 JADAPTIVE Limited. All Rights Reserved.
 *
 * For product documentation visit https://www.jadaptive.com/
 *
 * This file is part of Hypersocket JSON Client.
 *
 * Hypersocket JSON Client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hypersocket JSON Client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Hypersocket JSON Client.  If not, see http://www.gnu.org/licenses/.
 */
package com.logonbox.vpn.client.common.lbapi;

import java.util.List;

import jakarta.json.JsonObject;


public record Principal(String principalName, String principalDesc, Long id, PrincipalType type, List<Principal> groups) {

    public static Principal of(JsonObject obj) {
        return new Principal(
            obj.getString("principalName"),
            obj.getString("principalDesc", null),
            obj.getJsonNumber("id").longValue(),
            PrincipalType.valueOf(obj.getString("type")),
            obj.get("groups").asJsonArray().stream().map(jo -> Principal.of(jo.asJsonObject())).toList()
        );
    }

}
