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
package com.logonbox.vpn.client.gui.jfx;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class AppStreamHandler extends URLStreamHandler {

	public AppStreamHandler() {
	}

	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		String path = u.toExternalForm().substring(6);
		int idx = path.indexOf('/');
		if(idx != -1) {
			/* HACK: I cant get relative resource loading from classpath
			 * working correctly without loading the page via a custom
			 * URL and processing the (bad) paths it gets to correct them
			 */
			String n = path.substring(0, idx);
			if(n.endsWith(".html")) {
				path = path.substring(idx + 1);
			}
		}
		System.out.println("u " + u + " = " + path);
		URL resourceUrl = AppStreamHandler.class.getResource(path);
		if (resourceUrl == null)
			throw new IOException("Resource not found: " + u);

		return resourceUrl.openConnection();
	}
}