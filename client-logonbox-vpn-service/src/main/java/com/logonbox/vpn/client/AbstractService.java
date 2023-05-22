package com.logonbox.vpn.client;

import com.logonbox.vpn.client.dbus.VPNConnectionImpl;
import com.logonbox.vpn.client.dbus.VPNImpl;
import com.logonbox.vpn.client.ini.ConnectionRepositoryImpl;
import com.logonbox.vpn.client.service.ClientService;
import com.logonbox.vpn.client.service.ClientService.Listener;
import com.logonbox.vpn.client.service.ClientServiceImpl;
import com.logonbox.vpn.client.service.ConfigurationRepositoryImpl;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.ConfigurationItem;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionRepository;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.CustomCookieStore;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.net.CookieStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public abstract class AbstractService implements LocalContext, Listener {

	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-service";

	static Logger log = LoggerFactory.getLogger(AbstractService.class);

	private ScheduledExecutorService queue = Executors.newSingleThreadScheduledExecutor();
	private Map<String, VPNFrontEnd> frontEnds = Collections.synchronizedMap(new HashMap<>());
	private CookieStore cookieStore;
	private ConnectionRepository connectionRepository;
	private ClientServiceImpl clientService;

	private PromptingCertManager promptingCertManager;
	private ConfigurationRepositoryImpl configurationRepository;
	
	private final ResourceBundle bundle;
    private final PlatformService<?> platform;

	protected AbstractConnection conn;

	private VPN vpn;
	
	protected AbstractService(ResourceBundle bundle, PlatformService<?> platform) {
		this.bundle = bundle;
		this.platform = platform;
		
	}
	
	public VPN getVPN() {
		return vpn;
	}

	@Override
	public void shutdown(boolean restart) {
		close();
	}

	@Override
	public AbstractConnection getConnection() {
		return conn;
	}

	@Override
	public void sendMessage(Message message) {
		if(conn != null)
			conn.sendMessage(message);
	}

	public ClientService getClientService() {
		return clientService;
	}

	public PromptingCertManager getCertManager() {
		return promptingCertManager;
	}

	@Override
	public CookieStore getCookieStore() {
		if (cookieStore == null) {
			cookieStore = new CustomCookieStore(new File(AbstractDBusClient.CLIENT_HOME, "service-cookies.dat"));
		}
		return cookieStore;
	}

	@Override
	public PlatformService<?> getPlatformService() {
		return platform;
	}

	@Override
	public ScheduledExecutorService getQueue() {
		return queue;
	}

	@Override
	public boolean hasFrontEnd(String source) {
		return frontEnds.containsKey(source);
	}

	@Override
	public VPNFrontEnd getFrontEnd(String source) {
		return frontEnds.get(source);
	}

	@Override
	public void deregisterFrontEnd(String source) {
		synchronized (frontEnds) {
			VPNFrontEnd fe = frontEnds.remove(source);
			if (fe == null) {
				throw new IllegalArgumentException(String.format("Front end '%s' not registered.", source));
			} else
				log.info(String.format("De-registered front-end %s.", source));
		}
	}

	@Override
	public VPNFrontEnd registerFrontEnd(String source) {
		synchronized (frontEnds) {
			VPNFrontEnd fe = frontEnds.get(source);
			if (fe != null) {
				throw new IllegalArgumentException(String.format("Front end '%s' already registered.", source));
			}
			fe = new VPNFrontEnd(source);
			frontEnds.put(source, fe);
			return fe;
		}
	}

	@Override
	public Collection<VPNFrontEnd> getFrontEnds() {
		return frontEnds.values();
	}

	protected void onCleanUp() {
	}
	
	@Override
	public void close() {
		log.info("Shutdown clean up.");
		checkForUninstalling();
		clientService.stopService();
		try {
			getQueue().shutdown();
		}
		finally {
			if(conn != null) {
				try {
					conn.close();
				}
				catch(Exception e) {
					log.warn("Failed to close bus connection.");
				}
			}
			
			try {
				onCleanUp();
			}
			finally {
				try {
					connectionRepository.close();
				}
				catch(IOException ioe) {
					log.error("Failed to close connection repository.", ioe);
				}
			}
		}
	}
	
	protected ConfigurationRepositoryImpl getConfigurationRepository() {
		return configurationRepository;
	}

	protected void checkForUninstalling() {
		File flagFile = new File("uninstalling.txt");
		if(flagFile.exists()) {
			/* If uninstalling, stop all networks and tell all clients to exit
			 */
			try {
				sendMessage(new VPN.Exit("/com/logonbox/vpn"));
			} catch (DBusException e) {
			}
			try {
				for(Connection c : new ArrayList<>(clientService.getConnections(null))) {
					try {
						clientService.disconnect(c, "Shutdown");
					}
					catch(Exception e) {
					}
				}
				Thread.sleep(5000);
			}
			catch(Exception e) {
			}
		}
		flagFile.delete();
	}

	@Override
	public SSLParameters getSSLParameters() {
		return promptingCertManager.getSSLParameters();
	}

	@Override
	public SSLContext getSSLContext() {
		return promptingCertManager.getSSLContext();
	}

	@Override
	public final void configurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue) {
		if(getConnection() != null) {
			try {
				sendMessage(new VPN.GlobalConfigChange("/com/logonbox/vpn", item.getKey(), newValue == null ? "" : newValue.toString()));
			} catch (DBusException e) {
				throw new IllegalStateException("Failed to send event.", e);
			}
		}

		if (item == ConfigurationItem.LOG_LEVEL) {
			String value = (String)newValue;
			try {
				if (StringUtils.isBlank(value))
					setLevel(getDefaultLevel());
				else
					setLevel(Level.valueOf(value));
			}
			catch(Exception e) {
				if(log.isDebugEnabled())
					log.warn("Invalid log level, setting default. ", e);
				else
					log.warn("Invalid log level, setting default. " + e.getMessage());
				setLevel(getDefaultLevel());	
			}
		}
	}

	protected boolean publishDefaultServices() {

		try {
			/* DBus */
			if (log.isInfoEnabled()) {
				log.info(String.format("Exporting VPN services to DBus"));
			}
			vpn = new VPNImpl(this);
			conn.exportObject("/com/logonbox/vpn", vpn);
			if (log.isInfoEnabled()) {
				log.info(String.format("    VPN"));
			}
			for (ConnectionStatus connectionStatus : clientService.getStatus(null)) {
				Connection connection = connectionStatus.getConnection();
				log.info(String.format("    Connection %d - %s", connection.getId(), connection.getDisplayName()));
				conn.exportObject(String.format("/com/logonbox/vpn/%d", connection.getId()),
						new VPNConnectionImpl(this, connection));
			}

			return true;
		} catch (Exception e) {
			log.error("Failed to publish service", e);
			return false;
		}
	}

	protected boolean startServices() {
		log.info("Starting internal services.");
		promptingCertManager = new ServicePromptingCertManager(bundle, this);
		promptingCertManager.installCertificateVerifier();

		try {
			clientService.start();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start client configuration service.", e);
		}

		return true;
	}

	protected boolean buildServices() throws Exception {
		log.info("Using file data backend");
		connectionRepository = new ConnectionRepositoryImpl(platform);
		
		configurationRepository = new ConfigurationRepositoryImpl();
		clientService = new ClientServiceImpl(this, connectionRepository, configurationRepository);
		clientService.addListener(this);
		return true;

	}

	protected boolean startSavedConnections() {
		return clientService.startSavedConnections();
	}

	protected boolean isStrictSSL() {
		return "true".equals(System.getProperty("logonbox.vpn.strictSSL", "true"));
	}
}
