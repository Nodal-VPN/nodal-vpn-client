/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.logonbox.vpn.client;

import com.jadaptive.nodal.core.lib.PlatformService;
import com.jadaptive.nodal.core.lib.PlatformServiceFactory;
import com.logonbox.vpn.client.common.AppConstants;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionRepository;
import com.logonbox.vpn.client.common.CustomCookieStore;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.LoggingConfig.Audience;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.ini.ConnectionRepositoryImpl;
import com.logonbox.vpn.client.service.ClientService;
import com.logonbox.vpn.client.service.ClientService.Listener;
import com.logonbox.vpn.client.service.ClientServiceImpl;
import com.logonbox.vpn.client.service.ConfigurationRepositoryImpl;
import com.sshtools.jaul.AppRegistry.App;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public abstract class AbstractService<CONX extends IVpnConnection> implements LocalContext<CONX>, Listener {

	private ScheduledExecutorService queue = Executors.newSingleThreadScheduledExecutor();
	private CookieStore cookieStore;
	private ConnectionRepository connectionRepository;
	private ClientServiceImpl<CONX> clientService;
	private PromptingCertManager promptingCertManager;
	private ConfigurationRepositoryImpl configurationRepository;
    private PlatformService<?> platformService;
    private IVpn<CONX> vpn;
    
    private final ResourceBundle bundle;
    private final LoggingConfig logging;
    private final Optional<App> app;

	protected AbstractService(ResourceBundle bundle) {
		this.bundle = bundle;
        logging = createLoggingConfig();
        app = AppVersion.locateApp(getClass());
    }

    @Override
    public final LoggingConfig getLogging() {
        return logging;
    }
    
    protected abstract LoggingConfig createLoggingConfig();
	
	protected abstract Logger getLogger();

	@Override
	public final void close() {
		getLogger().info("Shutdown clean up.");
		checkForUninstalling();
		clientService.stopService();
		try {
		    getScheduler().shutdown();
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
					getLogger().error("Failed to close connection repository.", ioe);
				}
			}
		}
	}

	@Override
	public final void configurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue) {
		onConfigurationChange(item, oldValue, newValue);

		if (item == ConfigurationItem.LOG_LEVEL) {
			String value = (String)newValue;
			try {
				if (Utils.isBlank(value))
					getLogging().setLevel(getLogging().getDefaultLevel());
				else
				    getLogging().setLevel(Level.valueOf(value));
			}
			catch(Exception e) {
				if(getLogger().isDebugEnabled())
				    getLogger().warn("Invalid log level, setting default. ", e);
				else
				    getLogger().warn("Invalid log level, setting default. " + e.getMessage());
				getLogging().setLevel(getLogging().getDefaultLevel());
			}
		}
	}

	@Override
    public final  PromptingCertManager getCertManager() {
		return promptingCertManager;
	}

	@Override
    public final  ClientService<CONX> getClientService() {
		return clientService;
	}

	@Override
	public final  CookieStore getCookieStore() {
		if (cookieStore == null) {
		    var cookieDat = AppConstants.CLIENT_HOME.resolve("service-cookies.dat");
            try {
		        cookieStore = new CustomCookieStore(cookieDat.toFile());
		    }
		    catch(RuntimeException e) {
		        if(Files.exists(cookieDat)) {
		            try {
                        Files.delete(cookieDat);
                        cookieStore = new CustomCookieStore(cookieDat.toFile());
                    } catch (IOException e1) {
                        var tempMgr = new CookieManager();
                        cookieStore = tempMgr.getCookieStore();
                    }
		        }
		        else
		            throw e;
		    }
		}
		return cookieStore;
	}

	@Override
	public final PlatformService<?> getPlatformService() {
		return platformService;
	}

	@Override
	public final  ScheduledExecutorService getScheduler() {
		return queue;
	}

	@Override
	public final SSLContext getSSLContext() {
		return promptingCertManager.getSSLContext();
	}
    
    @Override
	public final SSLParameters getSSLParameters() {
		return promptingCertManager.getSSLParameters();
	}

	@Override
	public final  void shutdown(boolean restart) {
		close();
		onShutdown(restart);
	}

    @Override
	public Optional<IVpn<CONX>> getVpn() {
	    return Optional.ofNullable(vpn);
	}

	protected final boolean buildServices() throws Exception {
	    getLogger().info("Using file data backend");
        configurationRepository = new ConfigurationRepositoryImpl();
		connectionRepository = new ConnectionRepositoryImpl(configurationDir().resolve("ini"));
		clientService = new ClientServiceImpl<>(this, connectionRepository, configurationRepository);
		clientService.addListener(this);
		getLogger().info("Creating platform service");
		var fact = createPlatformServiceFactory();
        platformService = fact.createPlatformService(clientService);
        getLogger().info("Platform service is {}", platformService.getClass().getName());
        connectionRepository.start(platformService);
		return true;

	}
	
	protected abstract Path configurationDir();
    
    protected Audience calcLoggingAudience(Optional<Audience> selectedAudience) {
        return selectedAudience.orElseGet(() -> {
            if(AppVersion.isDeveloperWorkspace())
                return Audience.DEVELOPER;
            else {
                return Audience.USER;
            }   
        });
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

	protected PlatformServiceFactory createPlatformServiceFactory() {
//        var layer = getClass().getModule().getLayer();
//        if(layer == null) {
            return ServiceLoader.load(PlatformServiceFactory.class).findFirst().filter(p -> p.isSupported()).orElseThrow(() -> new UnsupportedOperationException(
                    String.format("%s not currently supported. There are no platform extensions installed, you may be missing libraries.", System.getProperty("os.name"))));
//        }
//        else {
//            return ServiceLoader.load(layer, PlatformServiceFactory.class).findFirst().filter(p -> p.isSupported()).orElseThrow(() -> new UnsupportedOperationException(
//                    String.format("%s not currently supported. There are no platform extensions installed, you may be missing libraries.", System.getProperty("os.name"))));
//        }
    }

	protected final ConfigurationRepositoryImpl getConfigurationRepository() {
		return configurationRepository;
	}
	
    protected boolean isStrictSSL() {
		return "true".equals(System.getProperty("logonbox.vpn.strictSSL", "true"));
	}

	protected void onCleanUp() {
	}
	
	protected abstract void onConfigurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue);

	protected void onShutdown(boolean restart) {
    }

    protected void onStartServices() {
	}

	protected boolean startSavedConnections() {
		return clientService.startSavedConnections();
	}

    protected final boolean createVpn() {
        getLogger().info("Starting vpn API.");
        var b = buildVpn();
        vpn = b.orElse(null);
        return vpn != null;
    }
    
    protected abstract Optional<IVpn<CONX>> buildVpn();
    
    protected final Optional<App> app() {
        return app;
    }

	protected final boolean startServices() {
	    getLogger().info("Starting internal services.");
		promptingCertManager = new ServicePromptingCertManager<CONX>(bundle, this);
		promptingCertManager.installCertificateVerifier();

		try {
			clientService.start(platformService);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start client configuration service.", e);
		}
		onStartServices();

		return true;
	}
	
}
