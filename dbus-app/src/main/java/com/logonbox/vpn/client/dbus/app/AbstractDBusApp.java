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
package com.logonbox.vpn.client.dbus.app;

import com.logonbox.vpn.client.app.AbstractApp;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.dbus.client.DBusVpnManager;
import com.sshtools.jaul.ArtifactVersion;
import com.sshtools.jaul.LocalAppDef;
import com.sshtools.jaul.Phase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Optional;

import picocli.CommandLine.Option;

public abstract class AbstractDBusApp extends AbstractApp<VpnConnection> {

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
    public Phase getPhase() {
        if (getVpnManager().isBackendAvailable()) {
            try {
                return Phase.valueOf(getVpnManager().getVpn().map(vpn -> vpn.getValue(ConfigurationItem.PHASE.getKey())).orElse(Phase.STABLE.name()));
            }
            catch(IllegalArgumentException iae) {
            }
        }
        return Phase.STABLE;
    }

    @Override
    public long getUpdatesDeferredUntil() {
        if (getVpnManager().isBackendAvailable()) {
            return getVpnManager().getVpn().map(vpn -> vpn.getLongValue(ConfigurationItem.DEFER_UPDATE_UNTIL.getKey())).orElse(0l);
        } else {
            return 0;
        }
    }

    @Override
    public final String getVersion() {
        return app().map(app -> {
            try {
                return new LocalAppDef(app).getVersion();
            }
            catch(Exception e) {
                return getArtifactVersion();
            }
        }).orElseGet(() -> getArtifactVersion());
    }

    @Override
    public final VpnManager<VpnConnection> getVpnManager() {
        return manager;
    }

    @Override
    public boolean isAutomaticUpdates() {
        if (getVpnManager().isBackendAvailable()) {
            return getVpnManager().getVpn().map(vpn -> vpn.getBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey())).orElse(false);
        } else {
            return false;
        }
    }

    @Override
    public void setAutomaticUpdates(boolean automaticUpdates) {
        if (getVpnManager().isBackendAvailable()) {
            getVpnManager().getVpn().orElseThrow(() -> new IllegalStateException("VPN not available.")).setBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey(), automaticUpdates);
        } else {
            log.warn("Failed to set automatic updates, not connected to bus.");
        }
    }

    @Override
    public void setPhase(Phase phase) {
        if (getVpnManager().isBackendAvailable()) {
            getVpnManager().getVpn().orElseThrow(() -> new IllegalStateException("VPN not available.")).setValue(ConfigurationItem.PHASE.getKey(), phase.name());
        } else {
            log.warn("Failed to set phase, not connected to bus.");
        }
    }

    @Override
    public void setUpdatesDeferredUntil(long timeMs) {
        if (getVpnManager().isBackendAvailable()) {
            getVpnManager().getVpn().orElseThrow(() -> new IllegalStateException("VPN not available.")).setLongValue(ConfigurationItem.DEFER_UPDATE_UNTIL.getKey(), timeMs);
        } else {
            log.warn("Failed to set defer time, not connected to bus.");
        }
    }

    protected DBusVpnManager.Builder buildVpnManager(DBusVpnManager.Builder builder) {
        builder.withBusAddress(busAddress);
        if(sessionBus)
            builder.withSessionBus();
        return builder;
    }

    @Override
    protected final Logger getLog() {
        return log;
    }

    protected Logger initApp() {
        getLogging().init(calcLoggingAudience(), level);

        /* Now we can start actually logging */
        log = LoggerFactory.getLogger(getClass());
        
        /*
         * There is a bit of a chicken and egg situation here. We need to initialise
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

    protected String getArtifactVersion() {
        return ArtifactVersion.getVersion("com.jadaptive", "nodal-vpn-client-dbus-app");
    }

    private final void initManager() {
        manager = buildVpnManager(new DBusVpnManager.Builder(this)).build();
    }
}
