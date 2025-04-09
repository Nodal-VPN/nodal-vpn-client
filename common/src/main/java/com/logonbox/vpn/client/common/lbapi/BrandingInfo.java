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

public record BrandingInfo(String foreground, String background, String name) {

	public static final String DEFAULT_FOREGROUND = "#ffffff";
	public static final String DEFAULT_BACKGROUND = "#0d0d0d";
	

	public static BrandingInfo of(JsonObject obj) {
	    return new BrandingInfo(
	       obj.getString("foreground", DEFAULT_FOREGROUND),
           obj.getString("background", DEFAULT_BACKGROUND),
           obj.getString("name", "JADAPTIVE")
	    );
	}
}
