package com.logonbox.vpn.client.service;

import static com.logonbox.vpn.client.common.Utils.isBlank;
import static java.util.Arrays.asList;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.ConfigurationRepository;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionRepository;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.ConnectionUtil;
import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.AuthMethod;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.UserCancelledException;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.drivers.lib.AbstractSystemContext;
import com.logonbox.vpn.drivers.lib.PlatformService;
import com.logonbox.vpn.drivers.lib.SystemConfiguration;
import com.logonbox.vpn.drivers.lib.VpnAdapter;
import com.logonbox.vpn.drivers.lib.VpnInterfaceInformation;
import com.logonbox.vpn.drivers.lib.util.Keys;
import com.logonbox.vpn.drivers.lib.util.Util;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.MultiValueMode;
import com.sshtools.liftlib.OS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.MessageFormat;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLHandshakeException;

import jakarta.json.Json;
import jakarta.json.JsonString;

public class ClientServiceImpl<CONX extends IVpnConnection> extends AbstractSystemContext implements ClientService<CONX> {
	private static final String X_VPN_RESPONSE = "X-VPN-Response";
	private static final String X_VPN_CHALLENGE = "X-VPN-Challenge";

	static Logger log = LoggerFactory.getLogger(ClientServiceImpl.class);

	static final int POLL_RATE = 30;

	private static final int AUTHORIZE_TIMEOUT = Integer
			.parseInt(System.getProperty("logonbox.vpn.authorizeTimeout", "300"));

	private static final String AUTHORIZE_URI = "/logonBoxVPNClient/";

	protected Map<Connection, VPNSession<CONX>> activeSessions = new HashMap<>();
	protected Map<Connection, ScheduledFuture<?>> authorizingClients = new HashMap<>();
	protected Map<Connection, VPNSession<CONX>> connectingSessions = new HashMap<>();
	protected Set<Connection> disconnectingClients = new HashSet<>();
	protected Set<Connection> temporarilyOffline = new HashSet<>();

	private ConfigurationRepository configurationRepository;
	private ConnectionRepository connectionRepository;
	private LocalContext<CONX> context;
	private Semaphore startupLock = new Semaphore(1);
	private AtomicLong transientConnectionId = new AtomicLong();
	private List<Listener> listeners = new ArrayList<>();
	private Set<Long> deleting = Collections.synchronizedSet(new LinkedHashSet<>());

	public ClientServiceImpl(LocalContext<CONX> context, ConnectionRepository connectionRepository,
			ConfigurationRepository configurationRepository) {
		this.context = context;
		this.configurationRepository = configurationRepository;
		this.connectionRepository = connectionRepository;

		try {
			startupLock.acquire();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	@Override
	public void authorized(Connection connection) {
        log.info("Authorizing {} [{}]", connection.getDisplayName(), connection.getUserPublicKey());
		synchronized (activeSessions) {
			if (!authorizingClients.containsKey(connection)) {
				throw new IllegalStateException("No authorization request.");
			}
			authorizingClients.remove(connection).cancel(false);
			temporarilyOffline.remove(connection);
		}
		save(connection);
        log.info(String.format("Authorized %s", connection.getDisplayName()));
        
        var task = createJob(connection);
        temporarilyOffline.remove(connection);
        task.setTask(context.getScheduler().schedule(() -> doConnect(task, false), 1, TimeUnit.MILLISECONDS));
	}

	@Override
	public void connect(Connection c) {
		synchronized (activeSessions) {
			checkValidConnect(c);
			if (log.isInfoEnabled()) {
				log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
			}
			if(configurationRepository.getValue(null, ConfigurationItem.SINGLE_ACTIVE_CONNECTION)) {
				while(activeSessions.size() > 0) {
					disconnect(activeSessions.values().iterator().next().getConnection(), "Switching to another server.");
				}
			}

            probeAuthMethods(c);
			c.setError(null);
			save(c);

			var task = createJob(c);
			temporarilyOffline.remove(c);
			task.setTask(context.getScheduler().schedule(() -> doConnect(task, true), 1, TimeUnit.MILLISECONDS));
		}
	}

	@Override
	public Connection connect(String owner, String uri) {
		synchronized (activeSessions) {
			if (hasStatus(owner, uri)) {
				Connection connection = getStatus(owner, uri).getConnection();
				connect(connection);
				return connection;
			}

			/* New temporary connection */
			Connection connection = connectionRepository.createNew();
			connection.setId(transientConnectionId.decrementAndGet());
			connection.updateFromUri(uri);
			connection.setConnectAtStartup(false);
			connection.setStayConnected(true);
			connect(connection);
			return connection;
		}
	}

	@Override
	public Connection importConfiguration(String owner, String configuration) {
		Connection connection;
		connection = connectionRepository.importConfiguration(configuration);
		connection.setOwner(owner);
		context.fireConnectionAdding();
		Connection newConnection = doSave(connection);
		context.connectionAdded(newConnection);
		return newConnection;
	}

	@Override
	public Connection create(String uri, String owner, boolean connectAtStartup, Mode mode, boolean stayConnected) {
		Connection connection;
		try {
			connection = connectionRepository.createNew(new URI(uri));
		} catch (URISyntaxException e1) {
			throw new IllegalStateException("Failed to create new connection.", e1);
		}
		connection.setOwner(owner);
		connection.setConnectAtStartup(connectAtStartup);
		connection.setMode(mode);
		connection.setStayConnected(stayConnected);
		context.fireConnectionAdding();
		generateKeys(connection);
        probeAuthMethods(connection);
		Connection newConnection = doSave(connection);
		context.connectionAdded(newConnection);
		return newConnection;
	}
    
    private boolean probeAuthMethods(Connection connection) {
        var client = createClient();
        var uri = connection.getUri(false);
        
        /* Special case. Lots connections will have a /app URI. This 
         * is still the default for new connections.
         *
         * So if we see this exact path, change it to /vpn, which will
         * be the new default as from version 4.0 of the client (which
         * will not be compatible with legacy VPN server).
         */
        if(uri.endsWith("/app")) {
            uri = uri.substring(0, uri.length() - 4) + "/vpn";
        }
        
        log.info("Check auth methods supported by {}.", uri);
        var builder = HttpRequest.newBuilder();
        var request = builder
                .version(Version.HTTP_1_1)
                .uri(URI.create(uri))
                .POST(BodyPublishers.noBody())
                .build();
        var changed = false;

        try {
            var response = client.send(request, BodyHandlers.ofString());
            var ctype = response.headers().firstValue("Content-Type").map(s -> s.split(";")[0]).orElse("text/plain");
            
            if(response.statusCode() != 200 || !ctype.equals("application/json")) {
                log.info("Server is < 3.0, assuming legacy authentication");
                var newModes = new AuthMethod[0];
                if(!Arrays.equals(newModes, connection.getAuthMethods())) {
                    connection.setAuthMethods(newModes);
                    changed = true;
                }
                if(connection.getMode().equals(Mode.MODERN)) {
                    /* Slight possiblity of down-grade */
                    connection.setMode(Mode.CLIENT);
                    changed = true;
                }
            }
            else {
                var methods =new ArrayList<AuthMethod>();
                var bdy = response.body();
                try(var rdr = Json.createReader(new StringReader(bdy))) {
                    var obj = rdr.readArray();
                    for(var el : obj) {
                        var mname = ((JsonString)el).getString();
                        try {
                            methods.add(AuthMethod.valueOf(mname));
                        }
                        catch(IllegalArgumentException iae) {
                            log.warn("Unsupported auth method {}, ignoring.", mname);
                        }   
                    }
                }
                
                var newModes = methods.toArray(new AuthMethod[0]);
                if(!Arrays.equals(newModes, connection.getAuthMethods())) {
                    connection.setAuthMethods(newModes);
                    changed = true;
                }
                if(!connection.getMode().equals(Mode.MODERN)) {
                    connection.setMode(Mode.MODERN);
                    changed = true;
                }
            }
        }
        catch(IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        catch(InterruptedException ie) {
            throw new IllegalStateException("Interrupted.", ie);
        }
        return changed;
    }

	@Override
	public void deauthorize(Connection connection) {
		synchronized (activeSessions) {
			log.info(String.format("De-authorizing connection %s", connection.getDisplayName()));
			connection.deauthorize();
			generateKeys(connection);
			temporarilyOffline.remove(connection);
			ScheduledFuture<?> f = authorizingClients.remove(connection);
			if (f != null)
				f.cancel(false);
		}
		save(connection);

	}

	@Override
	public void delete(Connection connection) {
		synchronized(deleting) {
			if(deleting.contains(connection.getId()))
				return;
			deleting.add(connection.getId());
		}
		try {
			try {
			    context.fireConnectionRemoving(connection);
				synchronized (activeSessions) {
					if (getStatusType(connection) == Type.CONNECTED) {
						disconnect(connection, null);
					}
				}
			} catch (Exception e) {
			    log.error("Failed to disconnect for deletion.", e);
				throw new IllegalStateException("Failed to disconnect.", e);
			}
			log.info("Deleting connection {}", connection.getDisplayName());
			connectionRepository.delete(connection);
			context.connectionRemoved(connection);
			log.info("Deleted connection {}", connection.getDisplayName());

		}  finally {
			deleting.remove(connection.getId());
		}
	}

	@Override
	public void disconnect(Connection c, String reason) {

		if (log.isInfoEnabled()) {
			log.info(String.format("Disconnecting connection with id %d, %s because '%s'", c.getId(), c.getHostname(),
					reason == null ? "" : reason));
		}
		boolean disconnect = false;
		VPNSession<CONX> wireguardSession = null;
		synchronized (activeSessions) {
			c.setError(Utils.isBlank(reason) ? null : reason);
			save(c);
			temporarilyOffline.remove(c);
			if (!disconnectingClients.contains(c)) {
				if (authorizingClients.containsKey(c)) {
					if (log.isInfoEnabled()) {
						log.info("Was authorizing, cancelling");
					}
					cancelAuthorize(c);
					disconnect = true;
				}
				if (activeSessions.containsKey(c)) {
					if (log.isInfoEnabled()) {
						log.info("Was connected, disconnecting");
					}
					disconnect = true;
					wireguardSession = activeSessions.remove(c);
				}
				if (connectingSessions.containsKey(c)) {
					if (log.isInfoEnabled()) {
						log.info("Was connecting, cancelling");
					}
					try {
						connectingSessions.get(c).close();
					} catch (IOException e) {
					}
					connectingSessions.remove(c);
					disconnect = true;
				}
			}

			if (!disconnect) {
				throw new IllegalStateException("Not connected.");
			}
			disconnectingClients.add(c);
		}

		try {
			try {

			    context.fireDisconnecting(c, reason);

				try {
					if (wireguardSession != null) {
						if (log.isInfoEnabled()) {
							log.info("Closing wireguard session");
						}
						wireguardSession.close();
						if (log.isInfoEnabled()) {
							log.info("WireGuard session closed");
						}
					}
				} catch (Exception e) {
					log.error("Failed to disconnect cleanly.", e);
				}
			} finally {
				disconnectingClients.remove(c);
			}
		} finally {
		    context.fireDisconnected(c, reason);
		}
	}

	public void failedToConnect(Connection connection, Throwable jpe) {
		synchronized (activeSessions) {
			log.info(String.format("Failed to connect %s.", connection.getDisplayName()));
			removeState(connection);
			connection.setError(jpe.getMessage());
			save(connection);
		}
	}

	protected void removeState(Connection connection) {
		synchronized (activeSessions) {
			log.info(String.format("Removing state.", connection.getDisplayName()));
			ScheduledFuture<?> f = authorizingClients.remove(connection);
			if (f != null)
				f.cancel(false);
			connectingSessions.remove(connection);
		}
	}

	@Override
	public String getActiveInterface(Connection c) {
		synchronized (activeSessions) {
			if (activeSessions.containsKey(c))
				return activeSessions.get(c).getInterfaceName().get();
			return null;
		}
	}

	@Override
	public Connection getConnectionStatus(Connection connection) throws IOException {
		if(!connection.isAuthorized() || Utils.isBlank(connection.getUserPrivateKey()))
			return connection;

        var client = createClient();

		try {
			var rootUri = connection.getUri(false);
			try {
				var newConnection = getConnectionStatusFromHost(rootUri, connection, client);
				if(newConnection != null)
					return newConnection;
			}
			catch(IOException ex) {
				// See https://logonboxlimited.slack.com/archives/C01218U3VC6/p1658533276788029
				try {
					var testUri = connection.getConnectionTestUri(false);
					if(Objects.equals(rootUri, testUri)) {
						/* Same URI, no need to fallback */
						throw ex;
					}
					else {
						log.warn("Hostname based URI check failed, falling back to IP address.");
						var newConnection = getConnectionStatusFromHost(testUri, connection, client);
						if(newConnection != null)
							return connection;
					}
				}
				catch(IOException ex2) {
					/* Failed to reach Http server, this is a hopefully a transient network error */
					log.info("Error is retryable.", ex);
					throw ex;
				}

			}
		}
		catch(InterruptedException ex) {
			throw new IOException("Interrupted.", ex);
		}

		/*
		 * The Http service appears to be there, and a VALID peer with this public key
		 * does not exist, so is likely an invalidated session.
		 */
		log.info("Error is not retryable, invalidate configuration. ");
		connection.deauthorize();
        generateKeys(connection);
		return save(connection);
	}

    private HttpClient createClient() {
        var prms = context.getSSLParameters();
        var ctx = context.getSSLContext();
        var client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(context.getCookieStore(), CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .sslParameters(prms)
                .sslContext(ctx)
                .version(Version.HTTP_1_1)
                .followRedirects(Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        return client;
    }

	private Connection getConnectionStatusFromHost(String rootUri, Connection connection, HttpClient client)
			throws IOException, InterruptedException {

		addConnectionCookie(connection, URI.create(rootUri));


        /* A simple ping first. This isn't strictly required, but getConnectionError()
         * does the same thing, so lets stick with it for now..
         */
		var pingUrl = rootUri + "/api/server/ping";
		var uriObj = URI.create(pingUrl);

		log.info(String.format("Testing if a connection to %s should be retried using %s.",
				connection.getDisplayName(), pingUrl));
		var uuid = getUUID(connection.getOwnerOrCurrent());
		var builder = HttpRequest.newBuilder();
		var request = builder
		         .uri(uriObj)
		         .version(Version.HTTP_1_1)
		         .build();
		var response = client.send(request, BodyHandlers.ofString());
		if(response.statusCode() != 200) {
			log.info(String.format("Server for %s appears to exist, but there was an error pinging (error %d).",
					connection.getDisplayName(), response.statusCode()));
			throw new IOException("Server returned a non-200 response.");
		}

		/* So the ping was OK (we dont care about the content of the response here, just that it happened), this means we either need to re-authorize, or somehow
		 * the server is no longer accepting wireguard packets, but is still running and
		 * accepting HTTP requests.
		 *
		 * It turns out this can happen, so we do need to check if we really need to
		 * re-authorize by seeing if our public key is still valid.
		 *
		 * For 2.4 servers, we also expect a X-VPN-Challenge response header, that we
		 * encrypt with our private key, and send the results in a X-VPN-Response header
		 * in the /configuration call.
		 */

		var checkUri = rootUri + "/api/peers/check/" + connection.getUserPublicKey();
		log.info(String.format("Testing if a configuration is actually valid for %s on %s.",
				connection.getDisplayName(), checkUri));
		var checkUriObj = URI.create(checkUri);
		request = builder
		         .uri(checkUriObj)
		         .header(VpnManager.DEVICE_IDENTIFIER, uuid.toString())
		         .version(Version.HTTP_1_1)
		         .build();

		response = client.send(request, BodyHandlers.ofString());
		if(response.statusCode() == 200) {
			/* This public key DOES exist. Let's see if we have any configuration updates */

			log.info(String.format("A peer with the key %s is still valid according to the server, so the problem is transient.", connection.getUserPublicKey()));

			var challenge = response.headers().firstValue(X_VPN_CHALLENGE).orElse("");
			if(challenge.equals("")) {
				log.info("Server appears to be a pre-2.4.0 server, cannot get configuration updates at this time.");
			}
			else {
				var configUri = rootUri + "/api/peers/configuration/" + connection.getUserPublicKey();
				log.info(String.format("Asking for latest configuration for %s on %s.",
						connection.getDisplayName(), configUri));

				/* Calculate the challenges response */
				log.info("Signing challenge: {} using public key of {}", challenge, connection.getUserPublicKey());

				//
				// TODO!!!!
				//
				// This is not secure at all. It is a placeholder until we can get
				// signature verification working.

				var signature = challenge;
				log.info("Signature: {}", signature);

				request = builder
				         .uri(URI.create(configUri))
				         .version(Version.HTTP_1_1)
						 .header(VpnManager.DEVICE_IDENTIFIER, getUUID(connection.getOwnerOrCurrent()).toString())
				         .header(X_VPN_RESPONSE, signature)
				         .build();

				response = client.send(request, BodyHandlers.ofString());
				if(response.statusCode() == 200) {

				    var rdr = new INIReader.Builder().
				            withMultiValueMode(MultiValueMode.SEPARATED).
				            build();

				    try {
    					var ini = rdr.read(response.body());

    					/* Interface (us) */
    					var interfaceSection = ini.section("Interface");
    					connection.setDns(Arrays.asList(interfaceSection.getAllOr("DNS", new String[0])));

    					connection.setPreUp(interfaceSection.contains("PreUp") ?  String.join("\n", interfaceSection.getAll("PreUp")) : "");
    					connection.setPostUp(interfaceSection.contains("PostUp") ? String.join("\n", interfaceSection.getAll("PostUp")) : "");
    					connection.setPreDown(interfaceSection.contains("PreDown") ? String.join("\n", interfaceSection.getAll("PreDown")) : "");
    					connection.setPostDown(interfaceSection.contains("PostDown") ? String.join("\n", interfaceSection.getAll("PostDown")) : "");

    					/* Custom LogonBox */
    					ini.sectionOr("LogonBox").ifPresent(s -> {
                            connection.setRouteAll(s.getBooleanOr("RouteAll", false));
    					});

    					/* Peer (them) */
    					var peerSection = ini.section("Peer");
    					var endpoint = peerSection.get("Endpoint");
    					int idx = endpoint.lastIndexOf(':');
    					connection.setEndpointAddress(endpoint.substring(0, idx));
    					connection.setEndpointPort(Integer.parseInt(endpoint.substring(idx + 1)));
    					connection.setPeristentKeepalive(peerSection.getInt("PersistentKeepalive"));
    					connection.setAllowedIps(Arrays.asList(interfaceSection.getAllOr("AllowedIPs", new String[0])));

    					return save(connection);
				    }
				    catch(ParseException pe) {
				        log.error("Failed to parse configuration update.", pe);
				    }
				}
				else {
					log.info("Server appeared to be a 2.4.0 server, but it did not response to a configuration update request. Assuming no config change.");
				}
			}
			return connection;
		}
		else if(response.statusCode() == 404) {
			/* Server is earlier than version 2.3.12 */
			log.info(String.format("This is a < 2.3.12 server, assuming re-authorization is needed (please upgrade).", connection.getUserPublicKey()));
		}
		else {
			/* This public key DOES NOT exist */
			log.info(String.format("No peer with the key %s is valid according to the server (error %d), so we need to re-authorize.", connection.getUserPublicKey(), response.statusCode()));
		}
		return null;
	}

	protected void addConnectionCookie(Connection connection, URI uri) {
		HttpCookie cookie = new HttpCookie(VpnManager.DEVICE_IDENTIFIER, getUUID(connection.getOwnerOrCurrent()).toString());
		cookie.setSecure(true);
		cookie.setPath("/");
		cookie.setDomain(connection.getHostname());
		context.getCookieStore().add(uri, cookie);
	}

	@Override
	public IOException getConnectionError(Connection connection) {

		try {
			try {
				if(doGetConnectionError(connection.getConnectionTestUri(false), connection))
					return null;
			}
			catch(IOException ex) {
				log.info("Check using IP address failed, trying using hostname.", ex);
				try {
					if(doGetConnectionError(connection.getUri(false), connection))
						return null;
				}
				catch(IOException ex2) {
					/* Failed to reach Http server, assume this is a transient network error and
					 * keep retrying */
					log.info("Error is retryable.", ex2);
					return ex;
				}
			}
		}
		catch(InterruptedException ex) {
			return new IOException("Interrupted.", ex);
		}

		/*
         * The Http service appears to be there, and a VALID peer with this public key
         * does not exist, so is likely an invalidated or new session.
         */
        log.info("Error is not retryable, invalidated or new configuration. ");
        return new ReauthorizeException(
                "Your configuration has been invalidated, and you will need to sign-on again.");
	}

	private boolean doGetConnectionError(String rootUri, Connection connection) throws IOException, InterruptedException {

		addConnectionCookie(connection, URI.create(rootUri));

		var client = HttpClient.newBuilder()
				.sslParameters(context.getSSLParameters())
				.sslContext(context.getSSLContext())
                .cookieHandler(new CookieManager(context.getCookieStore(), CookiePolicy.ACCEPT_ORIGINAL_SERVER))
		        .version(Version.HTTP_1_1)
		        .followRedirects(Redirect.NORMAL)
		        .connectTimeout(Duration.ofSeconds(20))
		        .build();

		var builder = HttpRequest.newBuilder();

		/* A simple ping first. This is mainly for backwards compatibility with servers
		 * prior to version 2.3.12.
		 */
		var uri = rootUri + "/api/server/ping";
		log.info(String.format("Testing if a connection to %s should be retried using %s.",
				connection.getDisplayName(), uri));
		var request = builder
				.version(Version.HTTP_1_1)
				.header(VpnManager.DEVICE_IDENTIFIER, getUUID(connection.getOwnerOrCurrent()).toString())
		        .uri(URI.create(uri))
		         .build();

		var response = client.send(request, BodyHandlers.ofString());
		if(response.statusCode() != 200) {
			log.info(String.format("Server for %s appears to exist, but there was an error pinging (error %d).",
					connection.getDisplayName(), response.statusCode()));
			throw new IOException("Server returned a non-200 response.");
		}

		/* So the ping was OK (we dont care about the content of the response here, just that it happened), this means we either need to re-authorize, or somehow
		 * the server is no longer accepting wireguard packets, but is still running and
		 * accepting HTTP requests.
		 *
		 * It turns out this can happen, so we do need to check if we really need to
		 * re-authorize by seeing if our public key is still valid.
		 */

		uri = rootUri + "/api/peers/check/" + connection.getUserPublicKey();
		log.info(String.format("Testing if a configuration is actually valid for %s on %s.",
				connection.getDisplayName(), uri));
		request = builder
				.version(Version.HTTP_1_1)
				.header(VpnManager.DEVICE_IDENTIFIER, getUUID(connection.getOwnerOrCurrent()).toString())
		        .uri(URI.create(uri))
		         .build();

		response = client.send(request, BodyHandlers.ofString());
		if(response.statusCode() == 200) {
			/* TODO This public key DOES exist */
			log.info(String.format("A peer with the key %s is still valid according to the server, so the problem is transient.", connection.getUserPublicKey()));
			return true;
		}
		else if(response.statusCode() == 404) {
			/* Server is earlier than version 2.3.12 */
			log.info(String.format("This is a < 2.3.12 server, assuming re-authorization is needed (please upgrade).", connection.getUserPublicKey()));
		}
		else {
			/* TODO This public key DOES NOT exist */
			log.info(String.format("No peer with the key %s is valid according to the server (error %d), so we need to re-authorize.", connection.getUserPublicKey(), response.statusCode()));
		}

		return false;
	}

	@Override
	public List<Connection> getConnections(String owner) {
		return connectionRepository.getConnections(owner);
	}

	@Override
	public boolean isMatchesAnyServerURI(String owner, String uri) {
		for(Connection c : getConnections(owner)) {
			if(uri.startsWith(c.getUri(false))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getDeviceName() {
		var hostname = PlatformUtilities.get().getHostname();
		if (Utils.isBlank(hostname)) {
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch (Exception e) {
				hostname = "Unknown Host";
			}
		}
		var os = System.getProperty("os.name");
		if (OS.isWindows()) {
			os = "Windows";
		} else if (OS.isLinux()) {
			os = "Linux";
		} else if (OS.isMacOs()) {
			os = "Mac OSX";
		}
		return os + " " + hostname;
	}

	@Override
	public String[] getKeys() {
		return ConfigurationItem.keys().toArray(new String[0]);
	}

	@Override
	public ConnectionStatus getStatus(long id) {
		synchronized (activeSessions) {
			List<ConnectionStatus> status = getStatus(null);
			for (ConnectionStatus s : status) {
				if (s.getConnection().getId() == id) {
					return s;
				}
			}
		}
		throw new IllegalStateException(String.format("Failed to get status for %d.", id));
	}

	@Override
	public List<ConnectionStatus> getStatus(String owner) {
		try {

			List<ConnectionStatus> ret = new ArrayList<>();
			Collection<Connection> connections = connectionRepository.getConnections(owner);
			List<Connection> added = new ArrayList<>();
			synchronized (activeSessions) {
				addConnections(ret, connections, added);
				addConnections(ret, activeSessions.keySet(), added);
				addConnections(ret, connectingSessions.keySet(), added);
			}
			return ret;
		}
		catch(Exception e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}

	}

	@Override
	public ConnectionStatus getStatus(String owner, String uri) {
		synchronized (activeSessions) {
			List<ConnectionStatus> status = getStatus(owner);
			for (ConnectionStatus s : status) {
                if (isSameUri(s.getConnection().getUri(true), uri)) {
                    return s;
                }
            }
            for (ConnectionStatus s : status) {
                if (isSameUri(s.getConnection().getUri(false), uri)) {
                    return s;
                }
            }
		}
		throw new IllegalArgumentException(String.format("No connection with URI %s.", uri));
	}
    
    static boolean isSameUri(String uri1, String uri2) {
        URI u1 = URI.create(uri1);
        URI u2 = URI.create(uri2);
        if(Objects.equals(u1.getScheme(), u2.getScheme()) && Objects.equals(u1.getHost(), u2.getHost())) {
            var p1 = u1.getPort() == -1 ? (u1.getScheme().equals("http") ? 80 : 443) : u1.getPort();
            var p2 = u2.getPort() == -1 ? (u2.getScheme().equals("http") ? 80 : 443) : u2.getPort();
            if(p1 == p2) {
                if(Objects.equals(u1.getUserInfo(), u2.getUserInfo())) {
                    return Objects.equals(translatePath(u1.getPath()), translatePath(u2.getPath()));
                }
            }
        }
        return false;
        
    }
    
    static String translatePath(String path) {
        if("/app".equals(path))
            path = "/vpn";
        return path;
    }

	@Override
	public ConnectionStatus getStatusForPublicKey(String publicKey) {
		synchronized (activeSessions) {
			List<ConnectionStatus> status = getStatus(null);
			for (ConnectionStatus s : status) {
				if (publicKey.equals(s.getConnection().getUserPublicKey())) {
					return s;
				}
			}
		}
		throw new IllegalArgumentException(String.format("Failed to get status for %s.", publicKey));
	}

	@Override
	public Type getStatusType(Connection c) {
		synchronized (activeSessions) {
			if (temporarilyOffline.contains(c))
				return Type.TEMPORARILY_OFFLINE;
			if (authorizingClients.containsKey(c))
				return Type.AUTHORIZING;
			if (activeSessions.containsKey(c))
				return Type.CONNECTED;
			if (connectingSessions.containsKey(c))
				return Type.CONNECTING;
			if (disconnectingClients.contains(c))
				return Type.DISCONNECTING;
			return Type.DISCONNECTED;
		}
	}

	@Override
	public LocalContext<CONX> getContext() {
		return context;
	}

	@Override
	public UUID getUUID(String owner) {
		UUID deviceUUID;
		var item = ConfigurationItem.DEVICE_UUID;
		String deviceUUIDString = configurationRepository.getValue(owner, item);
		if (deviceUUIDString.equals("")) {
			log.warn("New device UUID, resetting.");
			deviceUUID = UUID.randomUUID();
			configurationRepository.setValue(owner, item, deviceUUID.toString());
		} else {
			try {
				deviceUUID = UUID.fromString(deviceUUIDString);
			} catch (Exception e) {
				log.warn("Invalid device UUID, resetting.");
				deviceUUID = UUID.randomUUID();
				configurationRepository.setValue(owner, item, deviceUUID.toString());
			}
		}
		return deviceUUID;
	}

	@Override
	public <V> V getValue(String owner, ConfigurationItem<V> item) {
		return configurationRepository.getValue(owner, item);
	}

	@Override
	public boolean hasStatus(String owner, String uri) {
		synchronized (activeSessions) {
			List<ConnectionStatus> status = getStatus(owner);
			for (ConnectionStatus s : status) {
				if (s.getConnection().getUri(true).equals(uri)) {
					return true;
				}
			}
			for (ConnectionStatus s : status) {
				if (s.getConnection().getUri(false).equals(uri)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void ping() {
		// Noop
	}

	@Override
	public void requestAuthorize(Connection connection) {
		synchronized (activeSessions) {
			/* Can request multiple times */
			if (connectingSessions.containsKey(connection)) {
				throw new IllegalStateException("Already connecting.");
			}
			if (activeSessions.containsKey(connection)) {
				throw new IllegalStateException("Already connected.");
			}
			if (connection.isAuthorized())
				throw new IllegalStateException("Already authorized.");

			/*
			 * Setup a timeout so that clients don't get stuck authorizing if nobody is
			 * there to authorize.
			 *
			 * Put into the map before the event so the status would be correct if some
			 * client queried it.
			 */
			cancelAuthorize(connection);
			log.info(String.format("Setting up authorize timeout for %s", connection.getDisplayName()));
			authorizingClients.put(connection, context.getScheduler().schedule(() -> {
				disconnect(connection, "Authorization timeout.");
			}, AUTHORIZE_TIMEOUT, TimeUnit.SECONDS));

			log.info(String.format("Asking client to authorize %s", connection.getDisplayName()));
			context.fireAuthorize(connection, AUTHORIZE_URI);
		}
	}

	@Override
	public void restart() {
		log.info("Restarting with exit 99.");
		stopService();
		System.exit(99);
	}

	@Override
	public Connection save(Connection c) {
        context.fireConnectionUpdating(c);
		Connection newConnection = c;
		if(!c.isTransient()) {
			newConnection = doSave(c);
		}
		context.fireConnectionUpdated(newConnection);
		return newConnection;

	}

	public void scheduleConnect(Connection c, boolean reconnect) {
		synchronized (activeSessions) {
			checkValidConnect(c);
			if (log.isInfoEnabled()) {
                log.info("Scheduling connect for connection id {} / {} [{}]", c.getId(), c.getHostname(), c.getUserPublicKey());
			}
			Integer reconnectSeconds = configurationRepository.getValue(null, ConfigurationItem.RECONNECT_DELAY);
			Connection connection = connectionRepository.getConnection(c.getId());
			if (connection == null) {
				log.warn("Ignoring a scheduled connection that no longer exists, probably deleted.");
			} else {
				var job = createJob(c);
				job.setReconnect(reconnect);
				job.setTask(context.getScheduler().schedule(() -> doConnect(job, true), reconnectSeconds, TimeUnit.SECONDS));
			}
		}

	}

	@Override
	public <V> void setValue(String owner, ConfigurationItem<V> key, V value) {
		V was = configurationRepository.getValue(owner, key);
		if (!Objects.equals(was, value)) {
			configurationRepository.setValue(owner, key, value);
			for (int i = listeners.size() - 1; i >= 0; i--) {
				listeners.get(i).configurationChange(key, was, value);
			}
		}
	}

	public void start(PlatformService<?> platformService) throws Exception {

		var started = platformService.adapters();
		if (!started.isEmpty()) {
			log.warn(String.format("%d connections already active.", started.size()));
		}
		for (var session : started) {
		    session.configuration().firstPeer().ifPresent(peer -> {
		        try {
    	            var connection = getStatusForPublicKey(peer.publicKey()).getConnection();
    	            activeSessions.put(connection, new VPNSession<>(connection, context, session)); 
		        }
		        catch(Exception e) {
		            log.warn("Skipping {}, {}", peer.publicKey(), e.getMessage());
		        }
		    });
		}

		context.getScheduler().scheduleWithFixedDelay(() -> {
			try {
				resolveRemoteDependencies(context.getCertManager(), Util.checkEndsWithSlash(getExtensionStoreRoot()) + "api/store/repos2",
						new String[] { "logonbox-vpn-client" }, AppVersion.getVersion("com.logonbox", "client-logonbox-vpn-service"), AppVersion.getSerial(),
						"VPN Client", getCustomerInfo(), "CLIENT_SERVICE");
			} catch (Exception e) {
				if (log.isDebugEnabled()) {
					log.error("Failed to ping extension store.", e);
				}
			}
		}, 0, 1, TimeUnit.DAYS);


	}

	public boolean startSavedConnections() {

		try {
			log.info("Starting saved connections");
			var connected = 0;
			var connections = connectionRepository.getConnections(null);
			var attempts = 0;
            for (var c : connections) {
				if (c.isConnectAtStartup() && getStatusType(c) == Type.DISCONNECTED) {
				    attempts++;
					try {
						connect(c);
						connected++;
					} catch (Exception e) {
						log.error(String.format("Failed to start on-startup connection %s", c.getName()), e);
					}
				}
			}

			context.getScheduler().scheduleWithFixedDelay(() -> checkConnectionsAlive(), POLL_RATE, POLL_RATE, TimeUnit.SECONDS);

			return connected > 0 || attempts == 0;
		} catch (Exception e) {
			log.error("Failed to start service", e);
			return false;
		} finally {
			startupLock.release();
		}
	}

	@Override
	public void stopService() {
		synchronized (activeSessions) {
			activeSessions.clear();
			connectingSessions.clear();
			authorizingClients.clear();
			temporarilyOffline.clear();
		}
	}

	protected VPNSession<CONX> createJob(Connection c) {
		return new VPNSession<>(c, getContext());
	}

	protected boolean isUseAllCloudPhases() {
		return "true".equalsIgnoreCase(System.getProperty("logonbox.vpn.updates.useAllCloudPhases", "false"));
	}

	Connection doSave(Connection c) {
		// If a non-persistent connection is now being saved as a persistent
		// one, then update our maps
		boolean wasTransient = c.isTransient();
		Connection newConnection = connectionRepository.save(c);

		if (wasTransient) {
			log.info(String.format("Saving non-persistent connection, now has ID %d", newConnection.getId()));
		}

		synchronized (activeSessions) {
			if (activeSessions.containsKey(c)) {
				activeSessions.put(newConnection, activeSessions.remove(c));
			}
			if (authorizingClients.containsKey(c)) {
				authorizingClients.put(newConnection, authorizingClients.remove(c));
			}
			if (connectingSessions.containsKey(c)) {
				connectingSessions.put(newConnection, connectingSessions.remove(c));
			}
		}
		return newConnection;
	}

	private void addConnections(List<ConnectionStatus> ret, Collection<Connection> connections,
			List<Connection> added) {
		for (Connection c : connections) {
			if (!added.contains(c) && !deleting.contains(c.getId())) {
				var status = VpnInterfaceInformation.EMPTY;
				var session = activeSessions.get(c);
				if (session != null) {
					try {
					    status = session.getSession().get().information();
					} catch (UncheckedIOException e) {
                        throw new IllegalStateException(
								String.format("Failed to get status for an active connection %s on interface %s.",
										c.getDisplayName(), session.getInterfaceName().get()),
								e);
					}
				}
				ret.add(new ConnectionStatusImpl(c, status, getStatusType(c), AUTHORIZE_URI));
				added.add(c);
			}
		}
	}

	private ScheduledFuture<?> cancelAuthorize(Connection c) {
		ScheduledFuture<?> s = authorizingClients.remove(c);
		if (s != null) {
			log.info(String.format("Removed authorization timeout for %s", c.getUri(true)));
			s.cancel(false);
		}
		return s;
	}

	private void checkConnectionsAlive() {
		try {
			if(log.isDebugEnabled()) {
				log.debug("Checking {} active sessions to see if they are alive.", activeSessions.size());
			}
			synchronized (activeSessions) {
				for (var sessionEn : new HashMap<>(activeSessions).entrySet()) {
					Connection connection = sessionEn.getKey();

					if (temporarilyOffline.contains(connection)) {
						if(log.isDebugEnabled()) {
							log.info("{} is temporarily offline, checking if alive.", connection.getDisplayName());
						}
						/* Temporarily offline, are we still offline? */
						if (sessionEn.getValue().isAlive()) {
							/*
							 * If now completely alive again, remove from the list of temporarily offline
							 * connections and fire a connected event
							 */
							temporarilyOffline.remove(connection);
							log.info(String.format("%s back online.", connection.getDisplayName()));
							context.fireConnected(connection);

							/* Next session */
							continue;
						}
						else {
							/* Not alive, but it might have been invalidated while being temporarily
							 * offline. If that's the case, we won't be receiving any more handshakes,
							 * as there is no valid peer on the other end.
							 *
							 * In this case, we use the HTTP check again
							 */
							IOException reason = getConnectionError(connection);
							if (reason == null) {
								if (log.isDebugEnabled())
									log.debug("HTTP api seems good, but no wireguard handshakes (yet?). Wait.");
							} else if(reason instanceof ReauthorizeException) {
								if (log.isDebugEnabled())
									log.debug("Reason was reauthorization");

								if (connection.isAuthorized())
									deauthorize(connection);
								disconnect(connection, "Re-authorize required");
							} else {
								/* Stay temporarily offline */
								if(log.isDebugEnabled()) {
									log.debug("Staying temporarily offline.");
								}
							}
						}

						if (log.isDebugEnabled())
							log.debug(String.format("%s still temporarily offline.", connection.getDisplayName()));
					} else {

						if (!connection.isAuthorized() || authorizingClients.containsKey(connection)
								|| connectingSessions.containsKey(connection)
								|| sessionEn.getValue().isAlive()) {
							/*
							 * If not authorized, still 'connecting', 'authorizing', or completely alive,
							 * skip to next session
							 */
							if(log.isDebugEnabled()) {
								log.debug("Skipping to next session to check, this one ('{}' is not in valid state.", connection.getDisplayName());
							}
							continue;
						}

						/* Kill the dead session */
						log.info(String.format(
								"Session with public key %s hasn't had a valid handshake for %d seconds, disconnecting.",
								connection.getUserPublicKey(), ClientService.HANDSHAKE_TIMEOUT));

						/*
						 * Try to work out why .... we can only do this with LogonBox VPN because the
						 * HTTP service should be there as well. If there is an internet outage, or a
						 * problem on the server, then we can use this HTTP channel to try and get a
						 * little more info as to why we have no received a handshake for a while.
						 */
						IOException reason = getConnectionError(connection);
						if(reason == null) {
							log.warn("It appears the key is valid, so we have good HTTP comms. Only the wireguard channel appears to be down at the moment. We will assume the connection is still active, and keep polling.");
						}
						else {
							if (reason instanceof ReauthorizeException) {
								if (log.isDebugEnabled())
									log.debug("Reason was reauthorization");

								if (connection.isAuthorized())
									deauthorize(connection);
								disconnect(connection, "Re-authorize required");
							} else {

								if (log.isDebugEnabled())
									log.debug("Reason was {}", reason == null ? "NULL" : reason.getClass().getName());

								if (connection.isStayConnected()) {
									temporarilyOffline(connection, reason);
								} else {
									disconnect(connection, reason.getMessage());
								}
							}
						}
					}
				}
			}
		}
		catch(Exception e) {
			log.error("Check failure. ", e);
		}
	}

	protected void temporarilyOffline(Connection connection, Exception reason) {
		log.info("Signalling temporarily offline.");
		temporarilyOffline.add(connection);
		context.fireTemporarilyOffline(connection, reason);
	}

	private void checkValidConnect(Connection c) {
		synchronized (activeSessions) {
			if (connectingSessions.containsKey(c)) {
				throw new IllegalStateException("Already connecting.");
			}
			if (activeSessions.containsKey(c)) {
				throw new IllegalStateException("Already connected.");
			}
			if (authorizingClients.containsKey(c)) {
				throw new IllegalStateException("Currently authorizing.");
			}
		}
	}

	private void doConnect(VPNSession<CONX> job, boolean checkWithServer) {
		try {

			var connection = job.getConnection();
			connectingSessions.put(connection, job);

			if (log.isInfoEnabled()) {
				log.info("Connecting to " + connection);
			}

			/* Check status up front */
			if(checkWithServer && connection.isAuthorized()) {
				try {
					connection = getConnectionStatus(connection);
				}
				catch(Exception e) {
					log.error("Failed up-front connection test. Will continue for now.", e);
				}
			}

			try {

				/* Fire events */
			    context.fireConnecting(connection);

				job.open();
				if (log.isInfoEnabled()) {
					log.info("Ready to use " + connection);
				}

				if(ConnectionUtil.setLastKnownServerIpAddress(connection)) {
					if(!connection.isTransient())
						connection = doSave(connection);
				}

				synchronized (activeSessions) {
					connectingSessions.remove(connection);
					activeSessions.put(connection, job);

					/* Fire Events */
					context.fireConnected(connection);
				}

			} catch (Exception e) {
				if (e instanceof ReauthorizeException && connection.isLogonBoxVPN()) {
					var reason = getConnectionError(connection);
					if(reason == null) {
						failedToConnect(connection, e);
						if (log.isErrorEnabled()) {
							log.error("Failed to connect " + connection, e);
						}
					}
					else if(reason instanceof ReauthorizeException) {
						removeState(connection);
						log.info(String.format("Requested  reauthorization for %s", connection.getUri(true)));
						try {
							/*
							 * The connection did not get it's first handshake in a timely manner. We don't
							 * have determined that it is very likely it needs re-authorizing
							 */
							if (connection.isAuthorized())
								deauthorize(connection);
							requestAuthorize(connection);
							return;
						} catch (Exception e1) {
							if (log.isErrorEnabled()) {
								log.error("Failed to request authorization.", e1);
							}
						}
					}
					else {
						if(reason instanceof SSLHandshakeException) {
							/* Work around for SAN errors */
							if(reason.getMessage().indexOf("No subject alternative") != -1) {
								/* Return original error */
								e = reason;
							}
						}
						failedToConnect(connection, e);
						if (log.isErrorEnabled()) {
							log.error("Failed to connect " + connection, e);
						}
					}
				} else {
					failedToConnect(connection, e);
					if (log.isErrorEnabled()) {
						log.error("Failed to connect " + connection, e);
					}
				}

				var errorCauseText = new StringBuilder();
				var ex = e.getCause();
				while (ex != null) {
					if (!errorCauseText.toString().trim().equals("")
							&& !errorCauseText.toString().trim().endsWith(".")) {
						errorCauseText.append(". ");
					}
					if (ex.getMessage() != null)
						errorCauseText.append(ex.getMessage());
					ex = ex.getCause();
				}
				var trace = new StringWriter();
				e.printStackTrace(new PrintWriter(trace));
				context.fireFailed(connection, e.getMessage(), errorCauseText.toString(), trace.toString());

				if (!(e instanceof UserCancelledException)) {
					log.info(String.format("Connnection not cancelled by user. Reconnect is %s, stay connected is %s",
							job.isReconnect(), connection.isStayConnected()));
					if (connection.isStayConnected() && job.isReconnect()) {
						if (log.isInfoEnabled()) {
							log.info("Stay connected is set, so scheduling new connection to " + connection);
						}
						scheduleConnect(connection, true);
						return;
					}
				}
			}

		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Failed to get connection.", e);
			}
		}
	}

	private String getCustomerInfo() {
		try {
			var l = getConnections(null);
			if (l.isEmpty()) {
				return "New Install";
			} else {
				return l.iterator().next().getHostname();
			}
		} catch (Throwable t) {
			return "Default Install";
		}
	}

	private void generateKeys(Connection connection) {
		log.info("Generating private key");
		var key = Keys.genkey();
		connection.setUserPrivateKey(key.getBase64PrivateKey());
		connection.setUserPublicKey(key.getBase64PublicKey());
		log.info(String.format("Public key is %s", connection.getUserPublicKey()));
	}

	static String getExtensionStoreRoot() {
		var url = System.getProperty("hypersocket.archivesURL", "https://updates2.hypersocket.com/hypersocket/");
		return url.endsWith("/") ? url : url + "/";
	}

	public static void resolveRemoteDependencies(PromptingCertManager certManager, String url, String[] repos, String version, String serial,
			String product, String customer, String... targets) throws IOException {

	    var reposList = new ArrayList<String>(asList(repos));
		var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
				.sslContext(certManager.getSSLContext()).sslParameters(certManager.getSSLParameters())
				.connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
		try {

			var additionalRepos = System.getProperty("hypersocket.privateRepos");
			if (additionalRepos != null) {
				if (log.isInfoEnabled()) {
					log.info(String.format("Adding private repos %s", additionalRepos));
				}
				reposList.addAll(asList(additionalRepos.split(",")));
			}
			
			if (isBlank(additionalRepos)) {
				additionalRepos = System.getProperty("hypersocket.additionalRepos");
				if (additionalRepos != null) {
					if (log.isInfoEnabled()) {
						log.info(String.format("Adding additional repos %s", additionalRepos));
					}
	                reposList.addAll(asList(additionalRepos.split(",")));
				}
			}
			
			var updateUrl = String.format("%s/%s/%s/%s/%s", url, version, String.join(",", reposList), serial,
			        String.join(",", targets));

			if (log.isInfoEnabled()) {
				log.info("Checking for updates from " + updateUrl);
			}

			var request = HttpRequest.newBuilder(new URI(updateUrl)).header("Accept", "application/json")
					.header("Content-Type", "application/x-www-form-urlencoded").POST(ConnectionUtil.ofMap(Map.of(
	            "product", product,
	            "customer", customer
			))).build();

			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (log.isDebugEnabled()) {
				log.debug(response.body());
			}

		} catch (Exception ex) {
			throw new IOException("Failed to resolve remote extensions. " + ex.getMessage(), ex);
		}
	}

    @Override
    public ScheduledExecutorService queue() {
        return context.getScheduler();
    }

    @Override
    public SystemConfiguration configuration() {
        return new SystemConfiguration() {

            @Override
            public Duration serviceWait() {
                return Duration.ofSeconds(ClientService.SERVICE_WAIT_TIMEOUT);
            }

            @Override
            public boolean ignoreLocalRoutes() {
                return context.getClientService().getValue(null, ConfigurationItem.IGNORE_LOCAL_ROUTES);
            }

            @Override
            public Duration handshakeTimeout() {
                return Duration.ofSeconds(ClientService.HANDSHAKE_TIMEOUT);
            }

            @Override
            public Optional<String> dnsIntegrationMethod() {
                var val = context.getClientService().getValue(null, ConfigurationItem.DNS_INTEGRATION_METHOD);
                return val.equals("AUTO") ? Optional.empty() : Optional.of(val);
            }

            @Override
            public Optional<Integer> defaultMTU() {
                var val = context.getClientService().getValue(null, ConfigurationItem.MTU);
                return val == 0 ?Optional.empty() : Optional.of(val);
            }

            @Override
            public Optional<Duration> connectTimeout() {
                return Optional.of(Duration.ofSeconds(ClientService.CONNECT_TIMEOUT));
            }
        };
    }

    @Override
    public void addScriptEnvironmentVariables(VpnAdapter session, Map<String, String> env) {
        Connection connection = connectionRepository.getConnectionByPublicKey(session.configuration().publicKey());

        env.put("LBVPN_DEFAULT_DISPLAY_NAME", connection.getDefaultDisplayName());
        env.put("LBVPN_DISPLAY_NAME", connection.getDisplayName());
        env.put("LBVPN_HOSTNAME", connection.getHostname());
        env.put("LBVPN_NAME", connection.getName() == null ? "": connection.getName());
        env.put("LBVPN_ID", String.valueOf(connection.getId()));
        env.put("LBVPN_PORT", String.valueOf(connection.getPort()));

    }

    @Override
    public void alert(String message, Object... args) {
        if(args.length == 0)
            log.info("[ALERT] " + message);
        else
            log.info(MessageFormat.format(message, args));
    }
}
