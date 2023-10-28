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
