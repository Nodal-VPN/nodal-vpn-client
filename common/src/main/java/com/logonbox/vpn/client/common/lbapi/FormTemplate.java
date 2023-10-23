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
