package com.logonbox.vpn.client.common;

import com.sshtools.jaul.AppRegistry;
import com.sshtools.jaul.AppRegistry.App;
import com.sshtools.jaul.ArtifactVersion;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.Preferences;

import uk.co.bithatch.nativeimage.annotations.Resource;

@Resource("META-INF/maven/.*")
public class AppVersion {

	static Map<String, String> versions = Collections.synchronizedMap(new HashMap<>());


    public static Optional<App> locateApp(Class<?> appClass) {
        try {
            return Optional.of(AppRegistry.get().get(appClass));
        }
        catch(Exception e) {
            return Optional.empty();
        }
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

    public static String getVersion(Class<?> appClass, String groupId, String artifactId) {
        return locateApp(appClass).map(app -> app.asLocalApp().getVersion()).orElseGet(() -> ArtifactVersion.getVersion(groupId, artifactId));
    }
}
