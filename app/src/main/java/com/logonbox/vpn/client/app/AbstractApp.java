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
package com.logonbox.vpn.client.app;

import com.logonbox.vpn.client.common.AppConstants;
import com.logonbox.vpn.client.common.AppContext;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.CustomCookieStore;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.LoggingConfig.Audience;
import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.sshtools.jaul.AppCategory;
import com.sshtools.jaul.AppRegistry;
import com.sshtools.jaul.AppRegistry.App;
import com.sshtools.jaul.DummyUpdateService;
import com.sshtools.jaul.DummyUpdater.DummyUpdaterBuilder;
import com.sshtools.jaul.Install4JUpdateService;
import com.sshtools.jaul.Install4JUpdater.Install4JUpdaterBuilder;
import com.sshtools.jaul.JaulApp;
import com.sshtools.jaul.NoUpdateService;
import com.sshtools.jaul.UpdateDescriptor.MediaType;
import com.sshtools.jaul.UpdateService;
import com.sshtools.liftlib.OS;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.CookieStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@JaulApp(id = AbstractApp.TOOLBOX_APP_ID, category = AppCategory.GUI, updaterId = "54", updatesUrl = "https://sshtools-public.s3.eu-west-1.amazonaws.com/jaul-toolbox/${phase}/updates.xml")
public abstract class AbstractApp<CONX extends IVpnConnection> implements AppContext<CONX>, Callable<Integer> {

	final static int DEFAULT_TIMEOUT = 10000;
	
	public final static String TOOLBOX_APP_ID = "com.jadaptive.VPNClient";

	private ScheduledExecutorService scheduler;

	@Option(names = { "-u",
			"--as-user" }, description = "Act on behalf of another user, only an adminstrator can do this.")
	private String asUser;

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    protected Optional<Level> level;

    @Option(names = { "-LA",
            "--log-audience" }, paramLabel = "AUDIENCE", description = "Who is the audience for logging. USER is normal logging mode, with files in the most appropriate location for the application type. CONSOLE will log all output to console. DEVELOPER will log to a local `logs` directory.")
    private Optional<Audience> loggingAudience;

    @Option(names = { "--jaul-register" }, hidden = true, description = "Register this application with the JADAPTIVE update system and exit. Usually only called on installation.")
    private boolean jaulRegister;

    @Option(names = { "--jaul-deregister" }, hidden = true, description = "De-register this application from the JADAPTIVE update system and exit. Usually only called on uninstallation.")
    private boolean jaulDeregister;
    
    @Option(names = {"--jaul-packaging"}, description = "Type of package to register as", hidden = true)
    private Optional<MediaType> packaging;
	
	@Spec
	private CommandSpec spec;
	
	private PromptingCertManager certManager;
	private UpdateService updateService;
	private CookieStore cookieStore;
	private Properties instanceProperties = new Properties();
	private LoggingConfig logging;

    private Optional<App> app;

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
    public final Integer call() throws Exception {
        if(jaulDeregister) {
            AppRegistry.get().deregister(getClass());
            return 0;
        }
        else if(jaulRegister) {
            AppRegistry.get().register(getClass(), packaging.orElse(MediaType.INSTALLER));
            return 0;
        }
        else {
            return onCall();
        }
    }
	
	protected Optional<App> app() {
	    if(app == null) {
	        app = locateApp();
	    }
	    return app;
	}
	
	protected abstract int onCall() throws Exception;

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
               return new DummyUpdateService(this, () -> new DummyUpdaterBuilder().
                        withoutFail().
                        withUpdate().
                        onExit(() -> {
                    getLog().info("Exiting because updater signalled to do so.");
                    System.exit(0);
                }));
			}
			if("false" .equals(instanceProperties.get("userUpdates")) && !OS.isAdministrator()) {
				throw new Exception("Not an administrator, and userUpdates=false");
			}
            var app = locateApp().orElseThrow(() -> new IllegalStateException("Could not locate registered app."));
            getLog().info("Jaul Application ID: {}", app.getId());
            getLog().info("Install Directory: {}", app.getDir());
			return new Install4JUpdateService(this, () -> {
                return Install4JUpdaterBuilder.
			            builder().onExit(ret -> {
                            getLog().info("Exiting because updater signalled to do so with return code of {}.", ret);
                            System.exit(ret);
                        }).
			            withApp(app, this);
			});
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
			cookieStore = new CustomCookieStore(AppConstants.CLIENT_HOME.resolve("web-cookies.dat").toFile());
		}
		return cookieStore;
	}

	@Override
    public final  UpdateService getUpdateService() {
		if(updateService == null) {
			updateService = createUpdateService();
			getLog().info("Using update service: {}", updateService.getClass().getName());
		}
		return updateService;
	}

	@Override
    public final PromptingCertManager getCertManager() {
	    if(certManager == null) {
	        certManager = createCertManager();
	        if(certManager != null)
	            certManager.installCertificateVerifier();
	    }
		return certManager;
	}

    @Override
	public final ScheduledExecutorService getScheduler() {
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
			if (OS.isAdministrator())
				return asUser;
			else
				throw new IllegalStateException("Cannot impersonate a user if not an administrator.");
		}
	}

	protected abstract PromptingCertManager createCertManager();
	
    private Optional<App> locateApp() {
        try {
            return Optional.of(AppRegistry.get().launch(this.getClass()));
        }
        catch(Exception e) {
            return Optional.empty();
        }
    }

}
