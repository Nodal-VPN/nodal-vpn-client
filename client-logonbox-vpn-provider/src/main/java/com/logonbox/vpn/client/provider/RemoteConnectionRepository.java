package com.logonbox.vpn.client.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logonbox.vpn.ConnectionRepository;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.ini.ConnectionImpl;
import com.logonbox.vpn.client.ini.ConnectionRepositoryImpl;
import com.logonbox.vpn.common.client.Connection;

public class RemoteConnectionRepository extends ConnectionRepositoryImpl implements ConnectionRepository {

	final static Logger LOG = LoggerFactory.getLogger(RemoteConnectionRepository.class);

	public static final String MASTER_LIST = "https://vpn.logonbox.com/app/api/webhooks/publish/other-vpns";

	private Object lock = new Object();

	private Map<String, Connection> remoteConnections;
	private ScheduledFuture<?> remoteConnectionsTask;
	private boolean awaitingRemoteConnections = false;

	@Override
	public void init(LocalContext context) {
		this.context = context;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public Connection createNew() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Connection createNew(URI uriObj) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Connection update(URI uriObj, Connection connection) {
		throw new UnsupportedOperationException();
	}

	static String getKey(Connection c) {
		// return c.getHostname() + ":" + c.getPort();
		return String.valueOf(c.getId());
	}

	@Override
	public void prepare(Connection connection) {
		if (connection.getUserPrivateKey() == null) {
			context.getClientService().generateKeys(connection);
			save(connection);
		}
	}

	Map<String, Connection> getRemoteConnections(boolean force) {
		var remoteConnections = this.remoteConnections;
		if (remoteConnections == null) {
			synchronized (lock) {
				if(remoteConnectionsTask != null)
					/* Already waiting for new connections */
					return Collections.emptyMap();
				
				var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
						.sslContext(context.getCertManager().getSSLContext())
						.sslParameters(context.getCertManager().getSSLParameters())
						.connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

				try {
					HttpRequest request = HttpRequest.newBuilder(new URI(MASTER_LIST))
							.header("Accept", "application/json").GET().build();
					var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
					remoteConnections = Arrays
							.asList(new ObjectMapper().readValue(response.body(), ConnectionImpl[].class)).stream()
							.collect(Collectors.toMap((v) -> getKey(v), (v) -> v));
					awaitingRemoteConnections  = false;
					return this.remoteConnections = remoteConnections;

				} catch (IOException | InterruptedException | URISyntaxException ioe) {
					if(!awaitingRemoteConnections) {
						LOG.error("Failed to get server list.", ioe);
						awaitingRemoteConnections = true;
					}
					remoteConnectionsTask = context.getClientService().getTimer().schedule(() -> {
						remoteConnectionsTask = null;
						var c = getRemoteConnections(force);
						if(!c.isEmpty()) {
							LOG.info("Now have server list.");
							context.getClientService().remotesAvailable();
						}
					}, 5, TimeUnit.SECONDS);
					return Collections.emptyMap();
				}
			}
		} else {
			return remoteConnections;
		}
	}

	@Override
	public List<Connection> getConnections(String owner) {

		var org = new LinkedHashMap<>(
				super.getConnections(owner).stream().collect(Collectors.toMap((v) -> getKey(v), (v) -> v)));

		var known = getRemoteConnections(false);
		if(known.isEmpty()) {
			/* No servers currently available (master down?). We don't want
			 * to delete or update any existing profiles just yet in case
			 * it comes back
			 */
			return Collections.emptyList();
		}

		/*
		 * We only want connections to servers that are in the master list. So go
		 * through any existing connections and remove those that no longer exist.
		 */
		for (var it = org.entrySet().iterator(); it.hasNext();) {
			var c = it.next();
			if (!known.containsKey(c.getKey())) {
				delete(c.getValue());
				it.remove();
			} else {
				/*
				 * It does exist, so update some immutable details (always decided by the
				 * server)
				 */
				var k = known.get(c.getKey());
				var changed = false;
				if (!Objects.equals(c.getValue().getFlag(), k.getFlag())) {
					c.getValue().setFlag(k.getFlag());
					changed = true;
				}
				if (!Objects.equals(c.getValue().getHostname(), k.getHostname())) {
					c.getValue().setHostname(k.getHostname());
					changed = true;
				}
				if (!Objects.equals(c.getValue().getPort(), k.getPort())) {
					c.getValue().setPort(k.getPort());
					changed = true;
				}
				if (!Objects.equals(c.getValue().getName(), k.getName())) {
					c.getValue().setName(k.getName());
					changed = true;
				}
				if (!Objects.equals(c.getValue().getPath(), k.getPath())) {
					c.getValue().setPath(k.getPath());
					changed = true;
				}
				// TODO others?

				if (changed) {
					save(c.getValue());
				}
			}
		}

		/* Add any further connections that we have never saved */
		for (var c : known.values()) {
			c.setOwner(owner);
			if (!org.containsKey(getKey(c))) {
				org.put(getKey(c), c);
			}
		}

		var lst = new ArrayList<>(org.values());
		return lst;
	}

	@Override
	public Connection importConfiguration(String configuration) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isSingleConnection() {
		return true;
	}

	@Override
	public void close() throws IOException {
		super.close();
		if(remoteConnectionsTask != null)
			remoteConnectionsTask.cancel(false);
	}
}
