package com.logonbox.vpn.client.common;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilderFactory;

import uk.co.bithatch.nativeimage.annotations.Resource;

@Resource("META-INF/maven/.*")
public class AppVersion {

	static Map<String, String> versions = Collections.synchronizedMap(new HashMap<>());

	public static String getVersion(String groupId, String artifactId) {
		return getVersion(null, groupId, artifactId);
	}
	
	public static boolean isDeveloperWorkspace() {
		return new File("pom.xml").exists();
	}
    
    public static String getSerial() {
        var pref = Preferences.userRoot().node("com/hypersocket/json/version");
        var newPrefs = Preferences.userNodeForPackage(AppVersion.class);
        
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
	
	public static String getVersion(String installerShortName, String groupId, String artifactId) {
		String fakeVersion = Boolean.getBoolean("jadaptive.development")
				? System.getProperty("jadaptive.development.version", System.getProperty("jadaptive.devVersion"))
				: null;
		if (fakeVersion != null) {
			return fakeVersion;
		}

		String detectedVersion = versions.getOrDefault(groupId+ ":" + artifactId, "");
		if (!detectedVersion.equals(""))
			return detectedVersion;
		
		/* installed apps may have a .install4j/i4jparams.conf. If this XML
		 * file exists, it will contain the full application version which
		 * will have the build number in it too. 
		 */
		if(installerShortName != null) {
			try {
				var docBuilderFactory = DocumentBuilderFactory.newInstance();
				var docBuilder = docBuilderFactory.newDocumentBuilder();
				var appDir = new File(System.getProperty("install4j.installationDir", System.getProperty("user.dir")));
				var doc = docBuilder.parse(new File(new File(appDir, ".install4j"),"i4jparams.conf"));
				var el = doc.getDocumentElement().getElementsByTagName("general").item(0);
				var mediaName = el.getAttributes().getNamedItem("mediaName").getTextContent();
				if(mediaName.startsWith(installerShortName + "-")) {
					detectedVersion = el.getAttributes().getNamedItem("applicationVersion").getTextContent();
				}
			} catch (Exception e) {
			}
		}

		if (detectedVersion.equals("")) {		
	
			// try to load from maven properties first
			try {
				var p = new Properties();
				var is = AppVersion.class.getClassLoader()
						.getResourceAsStream("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");
				if (is == null) {
					is = AppVersion.class
							.getResourceAsStream("/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");
				}
				if (is != null) {
					try {
						p.load(is);
						detectedVersion = p.getProperty("version", "");
					} finally {
						is.close();
					}
				}
			} catch (Exception e) {
				// ignore
			}
		}

		// fallback to using Java API
		if (detectedVersion.equals("")) {
			var aPackage = AppVersion.class.getPackage();
			if (aPackage != null) {
				detectedVersion = aPackage.getImplementationVersion();
				if (detectedVersion == null) {
					detectedVersion = aPackage.getSpecificationVersion();
				}
			}
			if (detectedVersion == null)
				detectedVersion = "";
		}

		if (detectedVersion.equals("")) {
			try {
				var docBuilderFactory = DocumentBuilderFactory.newInstance();
				var docBuilder = docBuilderFactory.newDocumentBuilder();
				var doc = docBuilder.parse(new File("pom.xml"));
				if(doc.getDocumentElement().getElementsByTagName("name").item(0).getTextContent().equals(artifactId) && doc.getDocumentElement().getElementsByTagName("group").item(0).getTextContent().equals(groupId)) {
					detectedVersion = doc.getDocumentElement().getElementsByTagName("version").item(0).getTextContent();
				}
			} catch (Exception e) {
			}

		}

		if (detectedVersion.equals("")) {
			detectedVersion = "DEV_VERSION";
		}

		/* Treat snapshot versions as build zero */
		if (detectedVersion.endsWith("-SNAPSHOT")) {
			detectedVersion = detectedVersion.substring(0, detectedVersion.length() - 9) + "-0";
		}

		versions.put(groupId+ ":" + artifactId, detectedVersion);

		return detectedVersion;
	}
}
