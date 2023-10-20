package com.logonbox.vpn.client;

import com.logonbox.vpn.client.common.AbstractClient;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionRepository;
import com.logonbox.vpn.client.common.CustomCookieStore;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.ini.ConnectionRepositoryImpl;
import com.logonbox.vpn.client.service.ClientService;
import com.logonbox.vpn.client.service.ClientService.Listener;
import com.logonbox.vpn.client.service.ClientServiceImpl;
import com.logonbox.vpn.client.service.ConfigurationRepositoryImpl;
import com.logonbox.vpn.drivers.lib.PlatformService;
import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.net.CookieStore;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public abstract class AbstractService implements LocalContext, Listener {

	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-service";

	static Logger log = LoggerFactory.getLogger(AbstractService.class);

	private ScheduledExecutorService queue = Executors.newSingleThreadScheduledExecutor();
	private CookieStore cookieStore;
	private ConnectionRepository connectionRepository;
	private ClientServiceImpl clientService;

	private PromptingCertManager promptingCertManager;
	private ConfigurationRepositoryImpl configurationRepository;
	private final ResourceBundle bundle;
    private PlatformService<?> platformService;
    private final VpnManager vpnManager;

	protected AbstractService(ResourceBundle bundle, VpnManager vpnManager) {
		this.bundle = bundle;
		this.vpnManager = vpnManager;
	}

	@Override
    public final VpnManager getVPNManager() {
		return vpnManager;
	}

	@Override
	public final  void shutdown(boolean restart) {
		close();
	}

	@Override
    public final  ClientService getClientService() {
		return clientService;
	}

	@Override
    public final  PromptingCertManager getCertManager() {
		return promptingCertManager;
	}

	@Override
	public final  CookieStore getCookieStore() {
		if (cookieStore == null) {
			cookieStore = new CustomCookieStore(new File(AbstractClient.CLIENT_HOME, "service-cookies.dat"));
		}
		return cookieStore;
	}

	@Override
	public final PlatformService<?> getPlatformService() {
		return platformService;
	}

	@Override
	public final  ScheduledExecutorService getQueue() {
		return queue;
	}

	protected void onCleanUp() {
	}

	@Override
	public final void close() {
		log.info("Shutdown clean up.");
		checkForUninstalling();
		clientService.stopService();
		try {
			getQueue().shutdown();
		}
		finally {
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

	protected final ConfigurationRepositoryImpl getConfigurationRepository() {
		return configurationRepository;
	}

	protected final void checkForUninstalling() {
		File flagFile = new File("uninstalling.txt");
		if(flagFile.exists()) {
			/* If uninstalling, stop all networks and tell all clients to exit
			 */
		    fireExit();
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
	public final SSLParameters getSSLParameters() {
		return promptingCertManager.getSSLParameters();
	}

	@Override
	public final SSLContext getSSLContext() {
		return promptingCertManager.getSSLContext();
	}

	@Override
	public final void configurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue) {
		onConfigurationChange(item, oldValue, newValue);

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
	
    protected abstract void onConfigurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue);

	protected final boolean startServices() {
		log.info("Starting internal services.");
		promptingCertManager = new ServicePromptingCertManager(bundle, this);
		promptingCertManager.installCertificateVerifier();

		try {
			clientService.start(platformService);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start client configuration service.", e);
		}
		onStartServices();

		return true;
	}
	
	protected void onStartServices() {
	}

	protected final boolean buildServices() throws Exception {
		log.info("Using file data backend");
		connectionRepository = new ConnectionRepositoryImpl();
		configurationRepository = new ConfigurationRepositoryImpl();
		clientService = new ClientServiceImpl(this, connectionRepository, configurationRepository);
		clientService.addListener(this);
		log.info("Creating platform service");
		var fact = createPlatformServiceFactory();
        platformService = fact.createPlatformService(clientService);
        log.info("Platform service is {}", platformService.getClass().getName());
        connectionRepository.start(platformService);
		return true;

	}

    protected PlatformServiceFactory createPlatformServiceFactory() {
        return ServiceLoader.load(getClass().getModule().getLayer(), PlatformServiceFactory.class).findFirst().filter(p -> p.isSupported()).orElseThrow(() -> new UnsupportedOperationException(
                String.format("%s not currently supported. There are no platform extensions installed, you may be missing libraries.", System.getProperty("os.name"))));
    }

	protected boolean startSavedConnections() {
		return clientService.startSavedConnections();
	}

	protected boolean isStrictSSL() {
		return "true".equals(System.getProperty("logonbox.vpn.strictSSL", "true"));
	}
}
