package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVPNConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public interface DBusClient<CONX extends IVPNConnection> extends VpnManager<CONX> {

	default String getServerDBusAddress(String addressFile) {
		Properties properties = new Properties();
		Path file;
		if(addressFile != null)
			file = Paths.get(addressFile);
		else if (System.getProperty("lbvpn.dbus") != null) {
			file = Paths.get(System.getProperty("lbvpn.dbus"));
		} else if (Files.exists(Paths.get("pom.xml"))) {
		    /* Decide whether to look for side-by-side `dbus-daemon`, or if the
		     * service is running with an embedded bus (legacy mode)
		     */
		    var dbusDaemonProps = Paths.get("..", "dbus-daemon", "conf", "dbus.properties");
		    if(Files.exists(dbusDaemonProps))
		        file = dbusDaemonProps;
		    else
		        file = Paths.get("..", "service", "conf", "dbus.properties");
		} else {
            file = Paths.get("conf", "dbus.properties");
		}
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
