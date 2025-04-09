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
package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.api.IRemoteUI;
import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.io.Closeable;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface VpnManager<CONX extends IVpnConnection> {

    public interface Handle extends Closeable {
        @Override
        void close();
    }

    @FunctionalInterface
    public interface Authorize<CONX extends IVpnConnection> {

        void authorize(CONX connection, String uri, Mode mode, AuthMethod... authMethods);
    }

    @FunctionalInterface
    public interface Failure<CONX extends IVpnConnection> {

        void failure(CONX connection, String reason, String cause, String trace);
    }
    
    /**
     * Matches the identifier in logonbox VPN server
     * PeerConfigurationAuthenticationProvider.java
     */
    public static final String DEVICE_IDENTIFIER = "LBVPNDID";

    boolean isBackendAvailable();

    Optional<IVpn<CONX>> getVpn();
    
    Optional<IRemoteUI> getUserInterface();
    
    default IVpn<CONX> getVpnOrFail() {
        return getVpn().orElseThrow(() -> new IllegalStateException("Vpn service not available."));
    }
    
    Handle onConnectionAdded(Consumer<CONX> callback);
    
    Handle onConnectionUpdated(Consumer<CONX> callback);
    
    Handle onConnectionRemoved(Consumer<Long> callback);
    
    Handle onGlobalConfigChanged(BiConsumer<ConfigurationItem<?>, String> callback);
    
    Handle onVpnGone(Runnable callback);
    
    Handle onVpnAvailable(Runnable callback);
    
    Handle onAuthorize(Authorize<CONX> callback);
    
    Handle onConnecting(Consumer<CONX> callback);
    
    Handle onConnected(Consumer<CONX> callback);
    
    Handle onDisconnecting(BiConsumer<CONX, String> callback);
    
    Handle onDisconnected(BiConsumer<CONX, String> callback);
    
    Handle onTemporarilyOffline(BiConsumer<CONX, String> callback);
    
    Handle onBlocked(Consumer<CONX> callback);
    
    Handle onFailure(Failure<CONX> failure);

    default void checkVpnManagerAvailable() throws IllegalStateException {
        if(!isBackendAvailable())
            throw new IllegalStateException("Vpn manager not available.");
    }

    Handle onConnectionUpdating(Consumer<CONX> callback);

    Handle onConnectionRemoving(Consumer<CONX> callback);

    Handle onConnectionAdding(Runnable callback);

    void confirmExit();

    Handle onConfirmedExit(Runnable callback);

    void start();

}
