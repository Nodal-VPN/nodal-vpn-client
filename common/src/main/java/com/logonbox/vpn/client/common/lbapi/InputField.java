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

public record InputField(
	InputFieldType type,
	String resourceKey,
	String defaultValue,
	boolean required,
	String label,
	List<Option> options,
	String infoKey,
	String onChange,
	boolean readOnly,
	String classes,
	String help,
    boolean isLogonApiLink,
    boolean isValueResourceKey,
    Tag tag,
    String url,
    String alt,
    int width,
    String styleClass,
    boolean isAlert,
    String alertType) {	
	
    public enum Tag { h1, h2, h3, h4, h5, h6 };
    
    
    public static InputField of(JsonObject obj) {
        return new InputField(
            InputFieldType.valueOf(obj.getString("type")),
            obj.getString("resourceKey", null),
            obj.getString("defaultValue", null),
            obj.getBoolean("required", false),
            obj.getString("label", null),
            obj.getJsonArray("options").stream().map(jv -> Option.of(jv.asJsonObject())).toList(),
            obj.getString("infoKey", null),
            obj.getString("onChange", null),
            obj.getBoolean("readOnly", false),
            obj.getString("classes", null),
            obj.getString("help", null),
            obj.getBoolean("isLogonApiLink", false),
            obj.getBoolean("isValueResourceKey", false),
            obj.containsKey("tag") ? Tag.valueOf(obj.getString("tag")) : null,
            obj.getString("url", null),
            obj.getString("alt", null),
            obj.getInt("width", 0),
            obj.getString("styleClass", null),
            obj.getBoolean("isAlert", false),
            obj.getString("alertType", "danger")
        );
    }
}
