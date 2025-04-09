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

import java.net.MalformedURLException;
import java.net.URL;

public class Http {

	public static boolean isRemote(String newLoc) {
		return (newLoc.startsWith("http://") || newLoc.startsWith("https://"));
	}

	public static URL url(String base, String path) {
		try {
			return new URL(url(base), path);
		} catch (MalformedURLException murle) {
			throw new IllegalStateException("Not a supported URL.", murle);
		}
	}

	public static URL url(URL base, String path) {
		try {
			return new URL(base, path);
		} catch (MalformedURLException murle) {
			throw new IllegalStateException("Not a supported URL.", murle);
		}
	}

	public static URL url(String url) {
		try {
			return new URL(url);
		} catch (MalformedURLException murle) {
			throw new IllegalStateException("Not a supported URL.", murle);
		}
	}

	public static boolean isUrl(String url) {
		try {
			new URL(url);
			return true;

		} catch (MalformedURLException murle) {
		}
		return false;
	}

	public static URL parentURL(URL url) {
		return url(parentURL(url.toExternalForm()));
	}

	public static String parentURL(String urls) {
		while (urls.endsWith("/"))
			urls = urls.substring(0, urls.length() - 1);
		var lidx = urls.lastIndexOf('/');
		if (lidx == -1)
			return null;
		return urls.substring(0, lidx) + "/";
	}

}
