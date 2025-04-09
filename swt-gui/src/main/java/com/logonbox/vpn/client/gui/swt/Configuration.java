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
package com.logonbox.vpn.client.gui.swt;

import java.util.prefs.Preferences;

public class Configuration {

	public static final String DARK_MODE_AUTO = "auto";
	public static final String DARK_MODE_ALWAYS = "always";
	public static final String DARK_MODE_NEVER = "never";

	private String darkMode;
	private String logLevel;
	private String configurationFileDirectory;
	private int w;
	private int h;
	private int x;
	private int y;
	private boolean saveCookies;
	private Preferences node;

	//
	private final static Configuration DEFAULT_INSTANCE = new Configuration(
			Preferences.userNodeForPackage(Configuration.class));

	public Configuration(Preferences node) {
		this.node = node;
		x = node.getInt("x", 0);
		y = node.getInt("y", 0);
		w = node.getInt("w", 0);
		h = node.getInt("h", 0);

		darkMode = node.get("darkMode", DARK_MODE_AUTO);

		configurationFileDirectory = node.get("configurationFileDirectory", System.getProperty("user.home"));

		logLevel = node.get("logLevel", null);
		saveCookies = node.getBoolean("saveCookies", false);

	}

	public static Configuration getDefault() {
		return DEFAULT_INSTANCE;
	}

	public Boolean saveCookiesProperty() {
		return saveCookies;
	}

	public void saveCookiesProperty(boolean saveCookies) {
		this.saveCookies = saveCookies;
		node.putBoolean("saveCookies", saveCookies);
	}

	public String configurationFileProperty() {
		return configurationFileDirectory;
	}

	public void configurationFileProperty(String configurationFileDirectory) {
		this.configurationFileDirectory = configurationFileDirectory;
		node.put("configurationFileDirectory", configurationFileDirectory);
	}

	public int wProperty() {
		return w;
	}

	public int hProperty() {
		return h;
	}

	public int xProperty() {
		return x;
	}

	public int yProperty() {
		return y;
	}

	public void wProperty(int w) {
		node.putInt("w", w);
		this.w = w;
	}

	public void hProperty(int h) {
		node.putInt("h", h);
		this.h = h;
	}

	public void xProperty(int x) {
		node.putInt("x", x);
		this.x = x;
	}

	public void yProperty(int y) {
		node.putInt("y", y);
		this.y = y;
	}

	public String darkModeProperty() {
		return darkMode;
	}

	public void darkModeProperty(String darkMode) {
		this.darkMode = darkMode;
		node.put("darkMode", darkMode);
	}

	public String logLevelProperty() {
		return logLevel;
	}

	public void logLevelProperty(String logLevel) {
		this.logLevel = logLevel;
		node.put("logLevel", logLevel);
	}
}
