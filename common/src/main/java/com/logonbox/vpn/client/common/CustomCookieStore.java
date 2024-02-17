package com.logonbox.vpn.client.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomCookieStore implements CookieStore {
	public static final String DOMAIN_ATTR = "domain";

	static Logger log = LoggerFactory.getLogger(CustomCookieStore.class);

	private Map<URI, List<CookieWrapper>> cookies = new HashMap<>();

	private File file;

	@SuppressWarnings("unchecked")
	public CustomCookieStore(File file) {
		this.file = file;
		if (file.exists()) {
			try (ObjectInputStream oin = new ObjectInputStream(new FileInputStream(file))) {
				cookies = (Map<URI, List<CookieWrapper>>) oin.readObject();
				
				/* Turn to set to get rid of the duplicates. The bug that let this
				 * happen has been fixed, but it doesn't get corrected until new
				 * cookies are added. So do it once at start up too
				 */
				for(var en : cookies.entrySet()) {
					var s = new HashSet<>(en.getValue());
					en.getValue().clear();
					en.getValue().addAll(s);
				}
				
			} catch (Exception e) {
				log.warn("Could not load cookie jar, resetting.", e);
				save();
			}
		}
	}

	void save() {
		File p = file.getParentFile();
		if (!p.exists() && !p.mkdirs())
			throw new IllegalStateException("Could not create directory for cookie jar. " + p);
		try (ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream(file))) {
			oout.writeObject(cookies);
		} catch (IOException ioe) {
			throw new IllegalStateException("Could not save cookie jar.", ioe);
		}
	}

	@Override
	public boolean removeAll() {
		synchronized (cookies) {
			int size = cookies.size();
			cookies.clear();
			save();
			return size > 0;
		}
	}

	@Override
	public boolean remove(URI uri, HttpCookie cookie) {
		synchronized (cookies) {
			var k = getEffectiveURI(uri);
			List<CookieWrapper> l = cookies.get(k);
			CookieWrapper cookieW = new CookieWrapper(cookie);
			if (l != null && l.contains(cookieW)) {
				l.remove(cookieW);
				if (l.isEmpty())
					cookies.remove(k);
				save();
				return true;
			}
			return false;
		}
	}

	@Override
	public List<URI> getURIs() {
		return new ArrayList<>(cookies.keySet());
	}

	@Override
	public List<HttpCookie> getCookies() {
		List<HttpCookie> l = new ArrayList<>();
		for (List<CookieWrapper> v : cookies.values()) {
			for (CookieWrapper w : v) {
				l.add(w.toHttpCookie());
			}
		}
		return l;
	}

	@Override
	public List<HttpCookie> get(URI uri) {
		synchronized (cookies) {
			var k = getEffectiveURI(uri);
			List<HttpCookie> clist = cookies.containsKey(k)
					? cookies.get(k).stream().map(o -> o.toHttpCookie()).collect(Collectors.toList())
					: Collections.emptyList();
			return clist;
		}
	}

	@Override
	public void add(URI uri, HttpCookie cookie) {
		synchronized (cookies) {
			uri = getEffectiveURI(uri);
			List<CookieWrapper> l = cookies.get(uri);
			if (l == null) {
				l = new ArrayList<>();
				cookies.put(uri, l);
			}
			CookieWrapper w = new CookieWrapper(cookie);
			while(l.remove(w));
			l.add(w);
			save();
		}
	}

	private URI getEffectiveURI(URI uri) {
		URI eu = null;
		try {
			eu = new URI("http", uri.getHost(), null, null, null);
		} catch (URISyntaxException e) {
			eu = uri;
		}
		return eu;
	}
}