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
