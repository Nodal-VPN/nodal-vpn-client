package com.logonbox.vpn.client.common;

import org.w3c.dom.Document;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.Manifest;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class AppVersion {

	static Map<String,String> versions = Collections.synchronizedMap(new HashMap<>());
	
	public static String getVersion() {
		return getVersion("hypersocket-common");
	}
	
	public static String getSerial() {
        Preferences pref = Preferences.userRoot().node("com/hypersocket/json/version");
        Preferences newPrefs = Preferences.userNodeForPackage(AppVersion.class);
        
		var hypersocketId = System.getProperty("hypersocket.id", "hypersocket-one");
		if(pref.get(hypersocketId, null) != null) {
		    newPrefs.put("serial", hypersocketId);
		    pref.remove(hypersocketId);
		}
		
		var serial = newPrefs.get("serial", null);
		if(serial == null) {
		    serial = UUID.randomUUID().toString();
	        pref.put("serial", serial);
		}
		return serial;
	}
	
	public static String getVersion(String artifactId) {
		String fakeVersion = Boolean.getBoolean("hypersocket.development") ? 
				System.getProperty("hypersocket.development.version", System.getProperty("hypersocket.devVersion")) : null;
		if(fakeVersion != null) {
			return fakeVersion;
		}
		
	    String detectedVersion = versions.get(artifactId);
	    if(detectedVersion != null)
	    	return detectedVersion;

	    /* Load the MANIFEST.MF from all jars looking for the X-Extension-Version
	     * attribute. Any jar that has the attribute also can optionally have
	     * an X-Extension-Priority attribute. The highest priority is the
	     * version that will be used.
	     */
	    ClassLoader cl = Thread.currentThread().getContextClassLoader();
	    if(cl == null)
	    	cl = AppVersion.class.getClassLoader();
		try {
			int highestPriority = -1;
			String highestPriorityVersion = null;
		    for(Enumeration<URL> en = cl.getResources("META-INF/MANIFEST.MF");
		    		en.hasMoreElements(); ) {
		    	URL url = en.nextElement();
		    	try(InputStream in = url.openStream()) {
			    	Manifest mf = new Manifest(in);	
			    	String extensionVersion = mf.getMainAttributes().getValue("X-Extension-Version");
			    	if(Utils.isNotBlank(extensionVersion)) {
				    	String priorityStr = mf.getMainAttributes().getValue("X-Extension-Priority");
				    	int priority = Utils.isBlank(priorityStr) ? 0 : Integer.parseInt(priorityStr);
				    	if(priority > highestPriority) {
				    		highestPriorityVersion = extensionVersion;
				    	}
			    	}
		    	}
		    }
		    if(highestPriorityVersion != null)
		    	detectedVersion = highestPriorityVersion;
	    }
	    catch(Exception e) {

	    }

	    // try to load from maven properties first
		if(Utils.isBlank(detectedVersion)) {
		    try {
		        Properties p = new Properties();
		        InputStream is = cl.getResourceAsStream("META-INF/maven/com.hypersocket/" + artifactId + "/pom.properties");
		        if(is == null) {
			        is = AppVersion.class.getResourceAsStream("/META-INF/maven/com.hypersocket/" + artifactId + "/pom.properties");
		        }
		        if (is != null) { 
		            p.load(is);
		            detectedVersion = p.getProperty("version", "");
		        }
		    } catch (Exception e) {
		        // ignore
		    }
		}

	    // fallback to using Java API
		if(Utils.isBlank(detectedVersion)) {
	        Package aPackage = AppVersion.class.getPackage();
	        if (aPackage != null) {
	            detectedVersion = aPackage.getImplementationVersion();
	            if (detectedVersion == null) {
	                detectedVersion = aPackage.getSpecificationVersion();
	            }
	        }
	    }

		if(Utils.isBlank(detectedVersion)) {
	    	try {
	    		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
	            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
	            Document doc = docBuilder.parse (new File("pom.xml"));
	            detectedVersion = doc.getDocumentElement().getElementsByTagName("version").item(0).getTextContent();
	    	} catch (Exception e) {
			} 
	        
	    }

		if(Utils.isBlank(detectedVersion)) {
			detectedVersion = "DEV_VERSION";
		}

	    /* Treat snapshot versions as build zero */
	    if(detectedVersion.endsWith("-SNAPSHOT")) {
	    	detectedVersion = detectedVersion.substring(0, detectedVersion.length() - 9) + "-0";
	    }

	    versions.put(artifactId, detectedVersion);

	    return detectedVersion;
	}

	public static String getProductId() {
		return System.getProperty("hypersocket.id", "hypersocket-one");
	} 
	
	public static String getBrandId() {
		String id = getProductId();
		int idx = id.indexOf('-');
		if(idx==-1) {
			throw new IllegalStateException("Product id must consist of string formatted like <brand>-<product>");
		}
		return id.substring(0, idx);
	} 
}
