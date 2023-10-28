package com.logonbox.vpn.client.dbus.app;

import com.logonbox.vpn.client.app.AbstractApp;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.dbus.client.DBusVpnManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.nio.file.Path;
import java.util.Optional;

import picocli.CommandLine.Option;

public abstract class AbstractDBusApp extends AbstractApp<VpnConnection> {

	@Option(names = { "-a", "--address-file" }, description = "File to obtain DBUS address from.")
	private Optional<Path> addressFile;
	
	@Option(names = { "-ba", "--bus-address" }, description = "Use an alternative bus address.")
	private Optional<String> busAddress;
	
	@Option(names = { "-sb", "--session-bus" }, description = "Use session bus.")
	private boolean sessionBus;
	
	private VpnManager<VpnConnection> manager;
	
    protected Logger log;
	
	protected AbstractDBusApp() {
	    super();
	}
	
    @Override
    public final VpnManager<VpnConnection> getVpnManager() {
        return manager;
    }

    private final void initManager() {
        manager = buildVpnManager(new DBusVpnManager.Builder(this)).build();
    }

    protected Logger initApp() {
        getLogging().init(calcLoggingAudience(), level);

        /* Now we can start actually logging */
        log = LoggerFactory.getLogger(getClass());
        
        /*
         * There is a bit of a chicken and egg situation here. We need to initialising 
         * the manager (and so dbus) etc to get the shared configuration. But initialising
         * might produce logging, so we let this happen and reconfigure logging later
         */
        initManager();

        /*
         * Have backend now, so enough to get configuration for log level
         */
        getVpnManager().getVpn().ifPresent(vpn -> {
            Level cfgLevel = Level.valueOf(vpn.getValue(ConfigurationItem.LOG_LEVEL.getKey()));
            if (cfgLevel != null) {
                getLogging().setLevel(cfgLevel);
            } 
        });
        
        return log;
    }

    @Override
    protected final Logger getLog() {
        return log;
    }

    protected DBusVpnManager.Builder buildVpnManager(DBusVpnManager.Builder builder) {
        builder.withAddressFilePath(addressFile);
        builder.withBusAddress(busAddress);
        builder.withSessionBus(sessionBus);
        return builder;
    }
}
