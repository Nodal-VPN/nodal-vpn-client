package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVPNConnection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public interface DBusClient<CONX extends IVPNConnection> extends VpnManager<CONX> {

	default String getServerDBusAddress(String addressFile) {
		Properties properties = new Properties();
		String path;
		if(addressFile != null)
			path = addressFile;
		else if (System.getProperty("hypersocket.dbus") != null) {
			path = System.getProperty("hypersocket.dbus");
		} else if (Files.exists(Paths.get("pom.xml"))) {
			path = ".." + File.separator + "service" + File.separator + "conf" + File.separator + "dbus.properties";
		} else {
			path = "conf" + File.separator + "dbus.properties";
		}
		var file = Paths.get(path);
		if (Files.exists(file)) {
			try (var in = Files.newInputStream(file)) {
				properties.load(in);
				String addr = properties.getProperty("address", "");
				if (addr.equals(""))
					throw new IllegalStateException("DBus address file exists, but has no content.");
				return addr;
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to read DBus address file.", ioe);
			}
		} else
			return null;
	}

}
