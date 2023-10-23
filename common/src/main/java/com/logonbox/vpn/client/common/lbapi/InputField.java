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
