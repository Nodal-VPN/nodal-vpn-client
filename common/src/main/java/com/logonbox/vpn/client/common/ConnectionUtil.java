package com.logonbox.vpn.client.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public class ConnectionUtil {
    static Logger log = LoggerFactory.getLogger(ConnectionUtil.class);

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
			    log.info("Changing last known server IP address for connection {} to {}", connection.getId(), addr);
			    connection.setLastKnownServerIpAddress(addr);
			}
		}
		catch(Exception e) {
		    if(connection.getLastKnownServerIpAddress() != null) {
                log.info("Resetting last known server IP address for connection {}", connection.getId());
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
