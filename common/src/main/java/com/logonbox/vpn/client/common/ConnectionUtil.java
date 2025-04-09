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
package com.logonbox.vpn.client.common;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public class ConnectionUtil {

	public static HttpRequest.BodyPublisher ofMap(Map<String, String> parms) {
		var b = new StringBuilder();
		for(var m : parms.entrySet()) {
			if (b.length() > 0) {
	            b.append("&");
	        }
			b.append(URLEncoder.encode(m.getKey(), StandardCharsets.UTF_8));
			if(m.getValue() != null) {
				b.append('=');
				b.append(URLEncoder.encode(m.getValue(), StandardCharsets.UTF_8)); 
			}
		}
		return HttpRequest.BodyPublishers.ofString(b.toString());
	}

	public static String getUri(Connection connection) {
		if (connection == null) {
			return "";
		}
		return connection.getUri(false);
	}

	public static void prepareConnectionWithURI(URI uriObj, Connection connection) {
		if (!uriObj.getScheme().equals("https")) {
			throw new IllegalArgumentException("Only HTTPS is supported.");
		}

		connection.setHostname(uriObj.getHost());
		setLastKnownServerIpAddress(connection);
		connection.setPort(uriObj.getPort() <= 0 ? 443 : uriObj.getPort());
		connection.setConnectAtStartup(false);
		connection.setPath(uriObj.getPath());
	}

	public static boolean setLastKnownServerIpAddress(Connection connection) {
		var was = connection.getLastKnownServerIpAddress();
		try {
			var addr = InetAddress.getByName(connection.getHostname()).getHostAddress();
			if(!Objects.equals(addr, connection.getLastKnownServerIpAddress())) {
			    connection.setLastKnownServerIpAddress(addr);
			}
		}
		catch(Exception e) {
		    if(connection.getLastKnownServerIpAddress() != null) {
		        connection.setLastKnownServerIpAddress(null);
		    }
		}
		return !Objects.equals(was, connection.getLastKnownServerIpAddress());
	}

	public static URI getUri(String uriString) throws URISyntaxException {
		if (!uriString.startsWith("https://")) {
			if (uriString.indexOf("://") != -1) {
				throw new IllegalArgumentException("Only HTTPS is supported.");
			}
			uriString = "https://" + uriString;
		}
		while (uriString.endsWith("/"))
			uriString = uriString.substring(0, uriString.length() - 1);
		URI uri = new URI(uriString);
		if ("".equals(uri.getPath()))
			uri = uri.resolve("/app");
		return uri;
	}

}
