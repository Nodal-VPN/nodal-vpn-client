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
