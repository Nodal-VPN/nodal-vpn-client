package com.logonbox.vpn.client.app;

import com.logonbox.vpn.client.common.App;
import com.logonbox.vpn.client.common.AppContext;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.CustomCookieStore;
import com.logonbox.vpn.client.common.DummyUpdateService;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.LoggingConfig.Audience;
import com.logonbox.vpn.client.common.NoUpdateService;
import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.UpdateService;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.drivers.lib.util.OsUtil;

import org.slf4j.Logger;
import org.slf4j.event.Level;

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

public abstract class AbstractApp<CONX extends IVpnConnection> implements AppContext<CONX> {

	final static int DEFAULT_TIMEOUT = 10000;

	private ScheduledExecutorService scheduler;

	@Option(names = { "-u",
			"--as-user" }, description = "Act on behalf of another user, only an adminstrator can do this.")
	private String asUser;

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    protected Optional<Level> level;

    @Option(names = { "-LA",
            "--log-audience" }, paramLabel = "AUDIENCE", description = "Who is the audience for logging. USER is normal logging mode, with files in the most appropriate location for the application type. CONSOLE will log all output to console. DEVELOPER will log to a local `logs` directory.")
    private Optional<Audience> loggingAudience;
	
	@Spec
	private CommandSpec spec;
	
	private PromptingCertManager certManager;
	private UpdateService updateService;
	private CookieStore cookieStore;
	private Properties instanceProperties = new Properties();
	private LoggingConfig logging;

	protected AbstractApp() {
		
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
		logging = createLoggingConfig();
	}

	@Override
    public final LoggingConfig getLogging() {
        return logging;
    }
	
	protected abstract LoggingConfig createLoggingConfig();
	
	protected Audience calcLoggingAudience() {
	    return loggingAudience.orElseGet(() -> {
	        if(AppVersion.isDeveloperWorkspace())
	            return Audience.DEVELOPER;
	        else {
	            return Audience.USER;
	        }   
	    });
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
			cookieStore = new CustomCookieStore(App.CLIENT_HOME.resolve("web-cookies.dat").toFile());
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
    public final PromptingCertManager getCertManager() {
	    if(certManager == null) {
	        certManager = createCertManager();
	    }
		return certManager;
	}

    @Override
	public final ScheduledExecutorService getQueue() {
		return scheduler;
	}

	@Override
    public final void shutdown(boolean restart) {
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

    protected abstract Logger getLog();
    
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

	@Override
	public final String getEffectiveUser() {
		if (Utils.isBlank(asUser)) {
		    return PlatformUtilities.get().getCurrentUser();
		} else {
			if (OsUtil.isAdministrator())
				return asUser;
			else
				throw new IllegalStateException("Cannot impersonate a user if not an administrator.");
		}
	}

	protected abstract PromptingCertManager createCertManager();

}
