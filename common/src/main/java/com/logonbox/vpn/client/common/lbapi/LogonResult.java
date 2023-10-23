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

import jakarta.json.JsonObject;

public record LogonResult(
        String bannerMsg,
        String errorMsg,
        String errorStyle,
        boolean showLocales,
        boolean success,
        String version,
        Principal principal,
        boolean lastErrorIsResourceKey,
        FormTemplate formTemplate,
        boolean isNew,
        boolean isFirst,
        boolean isLast,
        boolean lastResultSuccessful,
        boolean inPostAuthentication,
        String lastButtonResourceKey,
        Realm realm,
        String location,
        Session session
    ) {
    
    public static LogonResult of(JsonObject obj) {
        return new LogonResult(
            obj.getString("bannerMsg", null),
            obj.getString("errorMsg", null),
            obj.getString("errorStyle", null),
            obj.getBoolean("showLocales", false),
            obj.getBoolean("success", false),
            obj.getString("version", null),
            obj.containsKey("principal") ? Principal.of(obj.getJsonObject("principal")) : null,
            obj.getBoolean("lastErrorIsResourceKey", false),
            obj.containsKey("formTemplate") ? FormTemplate.of(obj.getJsonObject("formTemplate")) : null,
            obj.getBoolean("isNew", false),
            obj.getBoolean("isFirst", false),
            obj.getBoolean("isLast", false),
            obj.getBoolean("lastResultSuccessful", false),
            obj.getBoolean("inPostAuthentication", false),
            obj.getString("lastButtonResourceKey", null),
            obj.containsKey("realm") ? Realm.of(obj.getJsonObject("realm")) : null,
            obj.getString("location", null),
            obj.containsKey("session") ? Session.of(obj.getJsonObject("session")) : null
        );
    }
}
