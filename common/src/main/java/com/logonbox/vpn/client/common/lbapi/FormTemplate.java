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

public record FormTemplate(
	String resourceKey,
	String scheme,
	List<InputField> inputFields,
	boolean showLogonButton,
	boolean showStartAgain,
	boolean overrideStartAgain,
	String logonButtonResourceKey,
	String logonButtonIcon,
	String formClass,
	Boolean startAuthentication
	) {
    
    public static FormTemplate of(JsonObject obj) {
        return new FormTemplate(
            obj.getString("resourceKey", null),
            obj.getString("scheme", null),
            obj.getJsonArray("inputFields").stream().map(v -> InputField.of(v.asJsonObject())).toList(),
            obj.getBoolean("showLogonButton", false),
            obj.getBoolean("showStartAgain", false),
            obj.getBoolean("overrideStartAgain", false),
            obj.getString("logonButtonResourceKey", null),
            obj.getString("logonButtonIcon", null),
            obj.getString("formClass", null),
            obj.getBoolean("startAuthentication", false)
        );
    }

}
