package com.logonbox.vpn.client.dbconvert;

import com.sshtools.jini.INI;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.INIReader.MultiValueMode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.util.Properties;

public class DbConvert {

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: dbconvert <dataDirectory> <connectionsDirectory>");
			System.exit(2);
		}

		var dbDir = Paths.get(args[0]);
		var cfgDir = Paths.get(args[1]);
		Files.createDirectories(cfgDir);
		var p = new Properties();
		p.put("user", "hypersocket");
		p.put("password", "hypersocket");
		p.put("create", "false");
		try (var conn = DriverManager.getConnection("jdbc:derby:" + dbDir.toAbsolutePath().toString(), p)) {
			try (var ps = conn.prepareStatement("select * from peer_configurations")) {
				try (var rs = ps.executeQuery()) {
					while (rs.next()) {
						var id = rs.getLong("id");

						var ini = INI.create();

						/* Interface (us) */
						var interfaceSection = ini.create("Interface");
						interfaceSection.put("Address", rs.getString("address"));
						var c = rs.getString("dns");
						if (c != null && c.length() > 0)
							interfaceSection.put("DNS", String.join(", ", c.split(",")));
						interfaceSection.put("PrivateKey", rs.getString("userPrivateKey"));
						c = rs.getString("preUp");
						if (c != null && c.length() > 0)
							interfaceSection.putAll("PreUp", c.split("\\n"));
						c = rs.getString("postUp");
						if (c != null && c.length() > 0)
							interfaceSection.putAll("PostUp", c.split("\\n"));
						c = rs.getString("preDown");
						if (c != null && c.length() > 0)
							interfaceSection.putAll("PreDown", c.split("\\n"));
						c = rs.getString("postDown");
						if (c != null && c.length() > 0)
							interfaceSection.putAll("PostDown", c.split("\\n"));

						/* Custom LogonBox */
						var logonBoxSection = ini.create("LogonBox");
						logonBoxSection.put("RouteAll", rs.getBoolean("routeAll"));
						logonBoxSection.put("Shared", rs.getBoolean("shared"));
						logonBoxSection.put("ConnectAtStartup", rs.getBoolean("connectAtStartup"));
						logonBoxSection.put("StayConnected", rs.getBoolean("stayConnected"));
						c = rs.getString("mode");
						logonBoxSection.put("Mode", c == null || c.length() == 0 ? "CLIENT" : c);
						c = rs.getString("owner");
						if (c != null && c.length() > 0)
							logonBoxSection.put("Owner", c);
						c = rs.getString("usernameHint");
						if (c != null && c.length() > 0)
							logonBoxSection.put("UsernameHint", c);
						c = rs.getString("hostname");
						if (c != null && c.length() > 0)
							logonBoxSection.put("Hostname", c);
						c = rs.getString("path");
						if (c != null && c.length() > 0)
							logonBoxSection.put("Path", c);
						c = rs.getString("error");
						if (c != null && c.length() > 0)
							logonBoxSection.put("Error", c);
						c = rs.getString("name");
						if (c != null && c.length() > 0)
							logonBoxSection.put("Name", c);
						logonBoxSection.put("Port", rs.getString("port"));
						var n = rs.getInt("mtu");
						if (n > 0)
							logonBoxSection.put("MTU", n);

						/* Peer (them) */
						c = rs.getString("publicKey");
						if (c != null && c.length() > 0) {
							var peerSection = ini.create("Peer");
							peerSection.put("PublicKey", c);
							peerSection.put("Endpoint",
									rs.getString("endpointAddress") + ":" + rs.getString("endpointPort"));
							peerSection.put("PersistentKeepalive", rs.getInt("peristentKeepalive"));
							c = rs.getString("allowedIps");
							if (c != null && c.length() > 0)
								peerSection.put("AllowedIps", String.join(", ", c.split(",")));
						}

						var wtr = new INIWriter.Builder().
	                            withMultiValueMode(MultiValueMode.SEPARATED).
						        build();
						try (var w = Files.newBufferedWriter(cfgDir.resolve(id + ".conf"))) {
							wtr.write(ini, w);
							w.flush();
						}
					}
				}
			}
		}
		Files.walk(dbDir).map(Path::toFile).sorted((o1, o2) -> -o1.compareTo(o2)).forEach(File::delete);
	}
}
