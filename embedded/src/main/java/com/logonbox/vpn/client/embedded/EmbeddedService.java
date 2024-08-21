package com.logonbox.vpn.client.embedded;

import com.logonbox.vpn.client.AbstractService;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;
import com.sshtools.jaul.ArtifactVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class EmbeddedService extends AbstractService<EmbeddedVpnConnection> {
    static Logger log = LoggerFactory.getLogger(EmbeddedService.class);

	/**
     * 
     */
    private final EmbeddedVpnManager main;
    private final Predicate<PlatformServiceFactory> filter;

	public EmbeddedService(ResourceBundle bundle, Predicate<PlatformServiceFactory> filter) throws Exception {
		super(bundle);
        this.main = new EmbeddedVpnManager(this);
		this.filter = filter;
		
		log.info("Starting in-process VPN service.");

		if (!buildServices()) {
			throw new Exception("Failed to build DBus services.");
		}

		if (!startServices()) {
			throw new Exception("Failed to start DBus services.");
		}
		
		if(!createVpn())
		    throw new Exception("Vpn instance could not be created.");

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
            public void run() {
				close();
			}
		});

		startSavedConnections();
	}
    
    @Override
    public String getVersion() {
        return ArtifactVersion.getVersion("com.logonbox", "client-logonbox-vpn-embedded");
    }

    @Override
    protected LoggingConfig createLoggingConfig() {
        return LoggingConfig.dumb();
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

	@Override
    protected PlatformServiceFactory createPlatformServiceFactory() {
	    /* Find first supported MobilePlatformService. This will delegate to the actual
	     * service, or one of the native mobile implementations.
	     */
        var layer = getClass().getModule().getLayer();
        Stream<Provider<PlatformServiceFactory>> impls;
        if(layer == null)
            impls = ServiceLoader.load(PlatformServiceFactory.class).stream();
        else
            impls = ServiceLoader.load(layer, PlatformServiceFactory.class).stream();
        return impls.filter(
                p -> filter.test(p.get()) && p.get().isSupported())
                .findFirst().map(p -> p.get())
                .orElseThrow(() -> new UnsupportedOperationException(String.format(
                        "The platform %s not currently supported. There are no platform extensions installed, you may be missing libraries.",
                        System.getProperty("os.name"))));
    }
	
	@Override
    protected Path configurationDir() {
        return Paths.get(System.getProperty("user.dir")).resolve("conf");
    }

    EmbeddedVpnConnection wrapConnection(Connection connection) {
	    return new EmbeddedVpnConnection(this, connection);
	}

    @Override
    public void fireAuthorize(Connection connection, String authorizeUri) {
        var wrapped = wrapConnection(connection);
        
        if(connection.getMode() == Mode.MODERN) {
            log.info("Asking client to authorize {} (modern)", connection.getDisplayName());
            this.main.onAuthorize().forEach(a -> a.authorize(wrapped, connection.getUri(false), connection.getMode(), connection.getAuthMethods()));
        }
        else {
            log.info("Asking client to authorize {} (legacy)", connection.getDisplayName());
            this.main.onAuthorize().forEach(a -> a.authorize(wrapped, authorizeUri, connection.getMode()));
        }
    }

    @Override
    public void fireConnectionUpdated(Connection connection) {
        var wrapped = wrapConnection(connection);
        this.main.onConnectionUpdated().forEach(c -> c.accept(wrapped));
    }

    @Override
    public void fireConnectionUpdating(Connection connection) {
        var wrapped = wrapConnection(connection);
        this.main.onConnectionUpdating().forEach(c -> c.accept(wrapped));
    }

    @Override
    public void fireTemporarilyOffline(Connection connection, Exception reason) {
        var wrapped = wrapConnection(connection);
        this.main.onTemporarilyOffline().forEach(c -> c.accept(wrapped, reason.getMessage() == null ? "" : reason.getMessage()));
    }

    @Override
    public void fireBlocked(Connection connection) {
        var wrapped = wrapConnection(connection);
        this.main.onBlocked().forEach(c -> c.accept(wrapped));
    }

    @Override
    public void fireConnected(Connection connection) {
        var wrapped = wrapConnection(connection);
        this.main.onConnected().forEach(c -> c.accept(wrapped));
    }

    @Override
    public void fireConnectionAdding() {
        this.main.onConnectionAdding().forEach(c -> c.run());
    }

    @Override
    public void connectionAdded(Connection connection) {
        var wrapped = wrapConnection(connection);
        this.main.onConnectionAdded().forEach(c -> c.accept(wrapped));
    }

    @Override
    public void fireConnectionRemoving(Connection connection) {
        var wrapped = wrapConnection(connection);
        this.main.onConnectionRemoving().forEach(c -> c.accept(wrapped));
    }

    @Override
    public void connectionRemoved(Connection connection) {
        this.main.onConnectionRemoved().forEach(c -> c.accept(connection.getId()));
    }

    @Override
    public void fireDisconnecting(Connection connection, String reason) {
        var wrapped = wrapConnection(connection);
        this.main.onDisconnecting().forEach(c -> c.accept(wrapped, reason));
    }

    @Override
    public void fireDisconnected(Connection connection, String reason) {
        var wrapped = wrapConnection(connection);
        this.main.onDisconnected().forEach(c -> c.accept(wrapped, reason));            
    }

    @Override
    public void fireConnecting(Connection connection) {
        var wrapped = wrapConnection(connection);
        this.main.onConnecting().forEach(c -> c.accept(wrapped));
    }

    @Override
    public void fireFailed(Connection connection, String message, String cause, String trace) {
        var wrapped = wrapConnection(connection);
        this.main.onFailure().forEach(c -> c.failure(wrapped, message, cause, trace));
    }

    @Override
    public void promptForCertificate(PromptType alertType, String title, String content, String key,
            String hostname, String message) {
       getCertManager().promptForCertificate(alertType, title, content, key, hostname, message);
    }

    @Override
    public void fireExit() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void onConfigurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue) {
        this.main.onGlobalConfigChanged().forEach(c -> c.accept(item, newValue == null ? "" : newValue.toString()));
    }

    @Override
    protected Optional<IVpn<EmbeddedVpnConnection>> buildVpn() {
        return Optional.of(new EmbeddedVpn(this));
    }

    EmbeddedVpnManager getVpnManager() {
        return main;
    }

}