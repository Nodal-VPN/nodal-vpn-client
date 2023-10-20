package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.api.IVPN;
import com.logonbox.vpn.drivers.lib.util.OsUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.CookieStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public abstract class AbstractClient implements VpnManager {

	public final static File CLIENT_HOME = new File(
			System.getProperty("user.home") + File.separator + ".logonbox-vpn-client");
	public final static File CLIENT_CONFIG_HOME = new File(CLIENT_HOME, "conf");

	final static int DEFAULT_TIMEOUT = 10000;
	static Logger log;

	private Object initLock = new Object();

	private ScheduledExecutorService scheduler;
	private IVPN vpn;

	@Option(names = { "-u",
			"--as-user" }, description = "Act on behalf of another user, only an adminstrator can do this.")
	private String asUser;
	@Spec
	private CommandSpec spec;
	private PromptingCertManager certManager;
	private UpdateService updateService;
	private CookieStore cookieStore;
	private Properties instanceProperties = new Properties();

	protected AbstractClient() {
		certManager = createCertManager();
		certManager.installCertificateVerifier();
		scheduler = Executors.newScheduledThreadPool(1);

		var instancePropertiesFile = Paths.get("conf/instance.properties");
		if(Files.exists(instancePropertiesFile)) {
			try(var fin = Files.newInputStream(instancePropertiesFile)) {
				instanceProperties.load(fin);
			}
			catch(IOException ioe) {
				// Don't care
			}
		}
	}

	protected final UpdateService createUpdateService() {
		try {
			if("true".equals(System.getProperty("logonbox.vpn.dummyUpdates"))) {
				return new DummyUpdateService(this);
			}
			if("false" .equals(instanceProperties.get("userUpdates")) && !OsUtil.isAdministrator()) {
				throw new Exception("Not an administrator, and userUpdates=false");
			}
			return new Install4JUpdateServiceImpl(this);
		} catch (Throwable t) {
			if(getLog().isDebugEnabled())
				getLog().info("Failed to create Install4J update service, using dummy service.", t);
			else
				getLog().info("Failed to create Install4J update service, using dummy service. {}", t.getMessage());
			return new NoUpdateService(this);
		}
	}

	@Override
    public final  CookieStore getCookieStore() {
		if (cookieStore == null) {
			cookieStore = new CustomCookieStore(new File(CLIENT_HOME, "web-cookies.dat"));
		}
		return cookieStore;
	}

	@Override
    public final  UpdateService getUpdateService() {
		if(updateService == null)
			updateService = createUpdateService();
		return updateService;
	}

	@Override
    public final Optional<IVPN> getVPN() {
		lazyInit();
		return Optional.of(vpn);
	}

	@Override
    public final PromptingCertManager getCertManager() {
		return certManager;
	}

	public final ScheduledExecutorService getScheduler() {
		return scheduler;
	}

	@Override
    public final void exit() {
		scheduler.shutdown();
		if(updateService != null)
			getUpdateService().shutdown();
	}

	protected final void init() throws Exception {

		if (vpn != null) {
			getLog().debug("Call to init when already have bus.");
			return;
		}

		if (getLog().isDebugEnabled())
			getLog().debug(String.format("Using update service %s", getUpdateService().getClass().getName()));

		onInit();

		getUpdateService().checkIfBusAvailable();

		/* Create cert manager */
		getLog().info("Cert manager: {}", getCertManager());
	}
	
    protected abstract void onInit() throws Exception;

	protected abstract boolean isInteractive();

	protected Logger getLog() {
		if (log == null) {
			log = LoggerFactory.getLogger(AbstractClient.class);
		}
		return log;
	}

	protected final void lazyInit() {
		synchronized (initLock) {
			if (vpn == null) {
				getLog().info("Trying to obtain Vpn Manager");
				try {
	                onLazyInit();   
				}
				catch (RuntimeException re) {
	                log.error("Failed to initialise.", re);
	                throw re;
	            } catch (Exception e) {
                    log.error("Failed to initialise.", e);
	                throw new IllegalStateException("Failed to initialize.", e);
	            }
			}
		}
	}
	
    protected abstract void onLazyInit() throws Exception;
    
    protected final void setVpn(IVPN vpn) {
        this.vpn = vpn;
    }

	protected final void handleCertPromptRequest(PromptType type, String key, String title, String content, String hostname, String message) {
		var cm = getCertManager();
		if(cm.isToolkitThread()) {
			if(cm.promptForCertificate(type, title, content, key, hostname, message)) {
				cm.accept(key);
			}
			else {
				cm.reject(key);
			}
		}
		else
			cm.runOnToolkitThread(() -> handleCertPromptRequest(type, key, title, content, hostname, message));
	}

	protected final String getEffectiveUser() {
		if (StringUtils.isBlank(asUser)) {
			String username = System.getProperty("user.name");
			if (SystemUtils.IS_OS_WINDOWS) {
				String domainOrComputer = System.getenv("USERDOMAIN");
				if (StringUtils.isBlank(domainOrComputer))
					return username;
				else
					return domainOrComputer + "\\" + username;
			} else {
				return username;
			}
		} else {
			if (OsUtil.isAdministrator())
				return asUser;
			else
				throw new IllegalStateException("Cannot impersonate a user if not an administrator.");
		}
	}

	protected abstract PromptingCertManager createCertManager();

}
