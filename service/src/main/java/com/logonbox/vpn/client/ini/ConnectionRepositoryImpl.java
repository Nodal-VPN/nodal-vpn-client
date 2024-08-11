package com.logonbox.vpn.client.ini;

import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionRepository;
import com.logonbox.vpn.client.common.ConnectionUtil;
import com.logonbox.vpn.drivers.lib.PlatformService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConnectionRepositoryImpl implements ConnectionRepository {

	static Logger log = LoggerFactory.getLogger(ConnectionRepositoryImpl.class);

	private Object session = new Object();
	private PlatformService<?> platformService;

    @Override
	public void start(PlatformService<?> platformService) {
		this.platformService = platformService;
	}

	@Override
	public Connection createNew() {
		return new ConnectionImpl();
	}

	@Override
	public Connection createNew(URI uriObj) {
		Connection newConnection = new ConnectionImpl();
		ConnectionUtil.prepareConnectionWithURI(uriObj, newConnection);
		return newConnection;
	}

	@Override
	public Connection update(URI uriObj, Connection connection) {
		ConnectionUtil.prepareConnectionWithURI(uriObj, connection);
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
				var dir = checkDir(dir());
				var f = resolve(dir, id);
				
				var temp = f.getParent().resolve(f.getFileName().toString() + ".tmp");
				try {
    				try (var w = Files.newBufferedWriter(temp)) {
    					ConnectionImpl.write(w, connection);
    					w.flush();
    				}
    				Files.move(temp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				}
				finally {
				    Files.deleteIfExists(temp);
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
		var id = 1l;
		var dir = dir();
		if(Files.exists(dir)) {
            try (var stream = Files.newDirectoryStream(dir)) {
    			for (var p : stream) {
    				var n = p.getFileName().toString();
    				if (n.endsWith(".conf")) {
    					id = Math.max(id, Long.parseLong(n.substring(0, n.length() - 5)) + 1);
    				}
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
			var c = new ArrayList<Connection>();
			var dir = dir();
            if(Files.exists(dir)) {
    			try (var stream = Files.newDirectoryStream(dir)) {
    				for (var p : stream) {
    					var name = p.getFileName().toString();
    					if (name.toLowerCase().endsWith(".conf")) {
    						try (BufferedReader r = Files.newBufferedReader(p)) {
                                long id = Long.parseLong(name.substring(0, name.length() - 5));
    						    try {
                                    c.add(new ConnectionImpl(id, r));
    						    }
    						    catch(Exception e) {
    						        log.error("Failed to load connection ID {} from {}, ignoring. Please examine and correct this file to prevent this error.", id, p);
    						    }
    						}
    					}
    				}
    			} catch (IOException ioe) {
    				throw new IllegalStateException("Failed to get configurations.", ioe);
    			}
			}
			return c;
		}
	}

	@Override
	public void delete(Connection con) {
		synchronized (session) {
			try {
			    var dir = dir();
			    if(Files.exists(dir)) {
    				var p = resolve(dir, con.getId());
    				if (Files.exists(p))
    					Files.delete(p);
			    }
			} catch (IOException e) {
				throw new IllegalStateException("Failed to delete configuration file.");
			}
		}
	}

	@Override
	public Connection getConnection(Long id) {
		Path path;
		synchronized (session) {
            var dir = dir();
			path = resolve(dir, id);
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
	
	Path resolve(Path dir, Long id) {
		return dir.resolve(id + ".conf");
	}

	Path dir() {
		return Paths.get("conf").resolve("ini");
	}
	
	Path checkDir(Path dir) {
	    if(!Files.exists(dir)) {
	        try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
	    }   
        return dir;
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public Connection importConfiguration(String configuration) {
		synchronized (session) {
			try {
				var id = getNextId();
				var dir = checkDir(dir());
				var f = resolve(dir, id);
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

    @Override
    public Connection getConnectionByPublicKey(String publicKey) {
        return getAllConnections().stream().filter(c -> publicKey.equals(c.getPublicKey())).findFirst().orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No such connection with public key {0}.", publicKey)));
    }

}
