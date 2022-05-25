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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logonbox.vpn.ConnectionRepository;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.ini.ConnectionImpl;
import com.logonbox.vpn.client.ini.ConnectionRepositoryImpl;
import com.logonbox.vpn.common.client.Connection;

public class RemoteConnectionRepository extends ConnectionRepositoryImpl implements ConnectionRepository {

	public static final String MASTER_LIST = "https://vpn.logonbox.com/app/api/webhooks/publish/other-vpns";
	
	private Map<String, List<Connection>> cachedConnections = new HashMap<>();
	private Object lock = new Object();

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
		//return c.getHostname() + ":" + c.getPort();
		return String.valueOf(c.getId());
	}

	@Override
	public void prepare(Connection connection) {
		if(connection.getUserPrivateKey() == null) {
			context.getClientService().generateKeys(connection);
			save(connection);
		}
	}

	@Override
	public List<Connection> getConnections(String owner) {
		var cacheKey = owner == null ? "" : owner;
		var thisCache = this.cachedConnections.get(cacheKey);
		if(thisCache == null) {
			synchronized(lock) {

				
				var org = new LinkedHashMap<>(
						super.getConnections(owner).stream().collect(Collectors.toMap((v) -> getKey(v), (v) -> v)));
				var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
						.sslContext(context.getCertManager().getSSLContext())
						.sslParameters(context.getCertManager().getSSLParameters()).connectTimeout(Duration.ofSeconds(15))
						.followRedirects(HttpClient.Redirect.NORMAL).build();

				try {
					HttpRequest request = HttpRequest.newBuilder(new URI(MASTER_LIST)).header("Accept", "application/json")
							.GET().build();

					var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
					ObjectMapper mapper = new ObjectMapper();
					var known = Arrays.asList(mapper.readValue(response.body(), ConnectionImpl[].class)).stream()
							.collect(Collectors.toMap((v) -> getKey(v), (v) -> v));
					;

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
							if(!Objects.equals(c.getValue().getFlag(), k.getFlag())) {
								c.getValue().setFlag(k.getFlag());
								changed = true;
							}
							if(!Objects.equals(c.getValue().getHostname(), k.getHostname())) {
								c.getValue().setHostname(k.getHostname());
								changed = true;
							}
							if(!Objects.equals(c.getValue().getPort(), k.getPort())) {
								c.getValue().setPort(k.getPort());
								changed = true;
							}
							if(!Objects.equals(c.getValue().getName(), k.getName())) {
								c.getValue().setName(k.getName());
								changed = true;
							}
							if(!Objects.equals(c.getValue().getPath(), k.getPath())) {
								c.getValue().setPath(k.getPath());
								changed = true;
							}
							// TODO others?
							
							if(changed) {
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
					cachedConnections.put(cacheKey, lst);
					return lst;
				} catch (IOException | InterruptedException | URISyntaxException ioe) {
					throw new IllegalStateException("Failed to get server list.", ioe);
				}
			}
		}
		else
			return thisCache;
	}

	@Override
	public Connection importConfiguration(String configuration) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isSingleConnection() {
		return true;
	}
}
