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
