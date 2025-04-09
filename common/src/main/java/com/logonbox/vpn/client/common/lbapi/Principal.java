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
