package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.api.IVPN;
import com.logonbox.vpn.client.common.api.IVPNConnection;
import com.logonbox.vpn.drivers.lib.util.OsUtil;
import com.sshtools.liftlib.OS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.CookieStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public abstract class AbstractClient<CONX extends IVPNConnection> implements VpnManager<CONX> {

	public final static File CLIENT_HOME = new File(
			System.getProperty("user.home") + File.separator + ".logonbox-vpn-client");
	public final static File CLIENT_CONFIG_HOME = new File(CLIENT_HOME, "conf");

	final static int DEFAULT_TIMEOUT = 10000;
	static Logger log;

	private Object initLock = new Object();

	private ScheduledExecutorService scheduler;
	private IVPN<CONX> vpn;

	@Option(names = { "-u",
			"--as-user" }, description = "Act on behalf of another user, only an adminstrator can do this.")
	private String asUser;
	@Spec
	private CommandSpec spec;
	private PromptingCertManager certManager;
	private UpdateService updateService;
	private CookieStore cookieStore;
	private Properties instanceProperties = new Properties();
    protected final List<Runnable> onConnectionAdding = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnectionAdded = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnectionRemoving = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<Long>> onConnectionRemoved = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnectionUpdated = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnectionUpdating = Collections.synchronizedList(new ArrayList<>());
    protected final List<BiConsumer<ConfigurationItem<?>, String>> onGlobalConfigChanged = Collections.synchronizedList(new ArrayList<>());
    protected final List<Runnable> onVpnGone = Collections.synchronizedList(new ArrayList<>());
    protected final List<Runnable> onConfirmedExit = Collections.synchronizedList(new ArrayList<>());
    protected final List<Runnable> onVpnAvailable = Collections.synchronizedList(new ArrayList<>());
    protected final List<Authorize<CONX>> onAuthorize = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnecting= Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnected= Collections.synchronizedList(new ArrayList<>());
    protected final List<BiConsumer<CONX, String>> onDisconnecting = Collections.synchronizedList(new ArrayList<>());
    protected final List<BiConsumer<CONX, String>> onDisconnected = Collections.synchronizedList(new ArrayList<>());
    protected final List<BiConsumer<CONX, String>> onTemporarilyOffline = Collections.synchronizedList(new ArrayList<>());
    protected final List<Failure<CONX>> onFailure = Collections.synchronizedList(new ArrayList<>());

	protected AbstractClient() {
		certManager = createCertManager();
		if(certManager != null)
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

	protected UpdateService createUpdateService() {
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
    public final Handle onConnectionAdded(Consumer<CONX> callback) {
        onConnectionAdded.add(callback);
        return () -> onConnectionAdded.remove(callback);
    }

    @Override
    public final Handle onConnectionAdding(Runnable callback) {
        onConnectionAdding.add(callback);
        return () -> onConnectionAdding.remove(callback);
    }

    @Override
    public final Handle onConnectionUpdating(Consumer<CONX> callback) {
        onConnectionUpdating.add(callback);
        return () -> onConnectionUpdating.remove(callback);
    }

    @Override
    public final Handle onConnectionUpdated(Consumer<CONX> callback) {
        onConnectionUpdated.add(callback);
        return () -> onConnectionUpdated.remove(callback);
    }

    @Override
    public final Handle onConnectionRemoved(Consumer<Long> callback) {
        onConnectionRemoved.add(callback);
        return () -> onConnectionRemoved.remove(callback);
    }

    @Override
    public final Handle onConnectionRemoving(Consumer<CONX> callback) {
        onConnectionRemoving.add(callback);
        return () -> onConnectionRemoving.remove(callback);
    }

    @Override
    public final Handle onGlobalConfigChanged(BiConsumer<ConfigurationItem<?>, String> callback) {
        onGlobalConfigChanged.add(callback);
        return () -> onGlobalConfigChanged.remove(callback);
    }

    @Override
    public final Handle onVpnGone(Runnable callback) {
        onVpnGone.add(callback);
        return () -> onVpnGone.remove(callback);
    }

    @Override
    public final Handle onConfirmedExit(Runnable callback) {
        onConfirmedExit.add(callback);
        return () -> onConfirmedExit.remove(callback);
    }

    @Override
    public final Handle onVpnAvailable(Runnable callback) {
        onVpnAvailable.add(callback);
        return () -> onVpnAvailable.remove(callback);
    }

    @Override
    public final Handle onAuthorize(Authorize<CONX> callback) {
        onAuthorize.add(callback);
        return () -> onAuthorize.remove(callback);
    }

    @Override
    public final Handle onConnecting(Consumer<CONX> callback) {
        onConnecting.add(callback);
        return () -> onConnecting.remove(callback);
    }

    @Override
    public final Handle onConnected(Consumer<CONX> callback) {
        onConnected.add(callback);
        return () -> onConnected.remove(callback);
    }

    @Override
    public final Handle onDisconnecting(BiConsumer<CONX, String> callback) {
        onDisconnecting.add(callback);
        return () -> onDisconnecting.remove(callback);
    }

    @Override
    public final Handle onDisconnected(BiConsumer<CONX, String> callback) {
        onDisconnected.add(callback);
        return () -> onDisconnected.remove(callback);
    }

    @Override
    public final Handle onTemporarilyOffline(BiConsumer<CONX, String> callback) {
        onTemporarilyOffline.add(callback);
        return () -> onTemporarilyOffline.remove(callback);
    }

    @Override
    public final Handle onFailure(Failure<CONX> callback) {
        onFailure.add(callback);
        return () -> onFailure.remove(callback);
    }

	@Override
    public final Optional<IVPN<CONX>> getVPN() {
		lazyInit();
		return Optional.ofNullable(vpn);
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
	    beforeExit();
	    try {
    		scheduler.shutdown();
    		if(updateService != null)
    			getUpdateService().shutdown();
	    }
	    finally {
	        afterExit();
	    }
	}
	
	protected void beforeExit() {
	}
    
    protected void afterExit() {
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
				    getLog().error("Failed to initialise.", re);
	                throw re;
	            } catch (Exception e) {
	                getLog().error("Failed to initialise.", e);
	                throw new IllegalStateException("Failed to initialize.", e);
	            }
			}
		}
	}
	
    protected abstract void onLazyInit() throws Exception;
    
    protected final void setVPN(IVPN<CONX> vpn) {
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
		if (Utils.isBlank(asUser)) {
			String username = System.getProperty("user.name");
			if (OS.isWindows()) {
				String domainOrComputer = System.getenv("USERDOMAIN");
				if (Utils.isBlank(domainOrComputer))
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
