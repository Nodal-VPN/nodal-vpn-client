package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.api.IVPN;
import com.logonbox.vpn.client.common.api.IVPNConnection;

import java.io.Closeable;
import java.net.CookieStore;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface VpnManager<CONX extends IVPNConnection> {

    public interface Handle extends Closeable {
        @Override
        void close();
    }

    @FunctionalInterface
    public interface Authorize<CONX extends IVPNConnection> {

        void authorize(CONX connection, String uri, Mode mode);
    }

    @FunctionalInterface
    public interface Failure<CONX extends IVPNConnection> {

        void failure(CONX connection, String reason, String cause, String trace);
    }
    
    /**
     * Matches the identifier in logonbox VPN server
     * PeerConfigurationAuthenticationProvider.java
     */
    public static final String DEVICE_IDENTIFIER = "LBVPNDID";

    boolean isBusAvailable();

    Optional<IVPN<CONX>> getVPN();
    
    default IVPN<CONX> getVPNOrFail() {
        return getVPN().orElseThrow(() -> new IllegalStateException("Vpn server not available."));
    }

    String getVersion();

    boolean isConsole();

    void exit();

    UpdateService getUpdateService();

    List<CONX> getVPNConnections();

    CONX getVPNConnection(long id);

    PromptingCertManager getCertManager();

    CookieStore getCookieStore();
    
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
    
    Handle onFailure(Failure<CONX> failure);

    default void checkVpnManagerAvailable() throws IllegalStateException {
        if(!isBusAvailable())
            throw new IllegalStateException("Vpn manager not available.");
    }

    Handle onConnectionUpdating(Consumer<CONX> callback);

    Handle onConnectionRemoving(Consumer<CONX> callback);

    Handle onConnectionAdding(Runnable callback);

}
