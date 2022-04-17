package com.logonbox.vpn.client.tray;

import java.util.prefs.Preferences;

public class Configuration {
	public static final String DARK_MODE = "darkMode";
	public static final String DARK_MODE_AUTO = "auto";
	public static final String DARK_MODE_ALWAYS = "always";
	public static final String DARK_MODE_NEVER = "never";

	private final static Configuration DEFAULT_INSTANCE = new Configuration(
			Preferences.userRoot().node("/com/logonbox/vpn/client/gui/jfx"));

	private Preferences node;

	public Configuration(Preferences node) {
		this.node = node;
	}

	public Preferences node() {
		return node;
	}

	public static Configuration getDefault() {
		return DEFAULT_INSTANCE;
	}

	public String darkMode() {
		return node.get("darkMode", DARK_MODE_AUTO);
	}
}
