package com.logonbox.vpn.client.ini;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionRepository;
import com.logonbox.vpn.common.client.Util;

public class ConnectionRepositoryImpl implements ConnectionRepository {

	static Logger log = LoggerFactory.getLogger(ConnectionRepositoryImpl.class);

	private Object session = new Object();
	private final PlatformService<?> platformService;

	public ConnectionRepositoryImpl(PlatformService<?> platformService) {
		this.platformService = platformService;
		try {
			Files.createDirectories(dir());
		} catch (IOException e) {
			throw new IllegalStateException("Could not create configuration directory.", e);
		}
	}

	@Override
	public Connection createNew() {
		return new ConnectionImpl();
	}

	@Override
	public Connection createNew(URI uriObj) {
		Connection newConnection = new ConnectionImpl();
		Util.prepareConnectionWithURI(uriObj, newConnection);
		return newConnection;
	}

	@Override
	public Connection update(URI uriObj, Connection connection) {
		Util.prepareConnectionWithURI(uriObj, connection);
		return connection;
	}

	@Override
	public Connection save(Connection connection) {
		synchronized (session) {
			var id = connection.getId();
			try {
				if (id == null || id == -1) {
					id = getNextId();
					connection.setId(id);
				}
				var f = resolve(id);
				try (var w = Files.newBufferedWriter(f)) {
					ConnectionImpl.write(w, connection);
					w.flush();
				}
				
				/* Only readable / writable by current user, i.e. administrator */ 
				platformService.restrictToUser(f);
				
				return connection;
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to save connection.", ioe);
			}
		}
	}

	protected Long getNextId() throws IOException {
		var id = 0l;
		try (var stream = Files.newDirectoryStream(dir())) {
			for (var p : stream) {
				var n = p.getFileName().toString();
				if (n.endsWith(".conf")) {
					id = Math.max(id, Long.parseLong(n.substring(0, n.length() - 5)) + 1);
				}
			}
		}
		return id;
	}

	@Override
	public List<Connection> getConnections(String owner) {
		return getAllConnections().stream().filter((c) -> owner == null || owner.equals(c.getOwner()) || c.isShared())
				.collect(Collectors.toList());
	}

	List<Connection> getAllConnections() {
		synchronized (session) {
			List<Connection> c = new ArrayList<>();
			try (var stream = Files.newDirectoryStream(dir())) {
				for (var p : stream) {
					var name = p.getFileName().toString();
					if (name.toLowerCase().endsWith(".conf")) {
						try (BufferedReader r = Files.newBufferedReader(p)) {
							c.add(new ConnectionImpl(Long.parseLong(name.substring(0, name.length() - 5)), r));
						}
					}
				}
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to get configurations.", ioe);
			}
			return c;
		}
	}

	@Override
	public void delete(Connection con) {
		synchronized (session) {
			try {
				var p = resolve(con.getId());
				if (Files.exists(p))
					Files.delete(p);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to delete configuration file.");
			}
		}
	}

	@Override
	public Connection getConnection(Long id) {
		Path path;
		synchronized (session) {
			path = resolve(id);
			if (!Files.exists(path)) {
				return null;
			}
		}
		try (BufferedReader r = Files.newBufferedReader(path)) {
			return new ConnectionImpl(id, r);
		} catch (IOException ioe) {
			throw new IllegalArgumentException("Failed to read configuration file.", ioe);
		}
	}

	Path resolve(Long id) {
		return dir().resolve(id + ".conf");
	}

	Path dir() {
		return Paths.get("conf").resolve("ini");
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public Connection importConfiguration(String configuration) {
		synchronized (session) {
			try {
				var id = getNextId();
				var f = resolve(id);
				try (var w = Files.newBufferedWriter(f)) {
					w.write(configuration);
					w.flush();
				}
				
				/* Only readable / writable by current user, i.e. administrator */ 
				platformService.restrictToUser(f);
				
				return getConnection(id);
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to import connection.", ioe);
			}
		}
	}

}
