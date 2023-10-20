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

public interface VpnManager {

    public interface Handle extends Closeable {
        @Override
        void close();
    }

    @FunctionalInterface
    public interface Authorize {

        void authorize(IVPNConnection connection, String uri, Mode mode);
    }

    @FunctionalInterface
    public interface Failure {

        void failure(IVPNConnection connection, String reason, String cause, String trace);
    }
    
    /**
     * Matches the identifier in logonbox VPN server
     * PeerConfigurationAuthenticationProvider.java
     */
    public static final String DEVICE_IDENTIFIER = "LBVPNDID";

    boolean isBusAvailable();

    Optional<IVPN> getVPN();
    
    default IVPN getVPNOrFail() {
        return getVPN().orElseThrow(() -> new IllegalStateException("Vpn server not available."));
    }

    String getVersion();

    boolean isConsole();

    void exit();

    UpdateService getUpdateService();

    List<IVPNConnection> getVPNConnections();

    IVPNConnection getVPNConnection(long id);

    PromptingCertManager getCertManager();

    CookieStore getCookieStore();
    
    Handle onConnectionAdded(Consumer<IVPNConnection> callback);
    
    Handle onConnectionUpdated(Consumer<IVPNConnection> callback);
    
    Handle onConnectionRemoved(Consumer<Long> callback);
    
    Handle onGlobalConfigChanged(BiConsumer<ConfigurationItem<?>, String> callback);
    
    Handle onVpnGone(Runnable callback);
    
    Handle onVpnAvailable(Runnable callback);
    
    Handle onAuthorize(Authorize callback);
    
    Handle onConnecting(Consumer<IVPNConnection> callback);
    
    Handle onConnected(Consumer<IVPNConnection> callback);
    
    Handle onDisconnecting(BiConsumer<IVPNConnection, String> callback);
    
    Handle onDisconnected(BiConsumer<IVPNConnection, String> callback);
    
    Handle onTemporarilyOffline(BiConsumer<IVPNConnection, String> callback);
    
    Handle onFailure(Failure failure);

    default void checkVpnManagerAvailable() throws IllegalStateException {
        if(!isBusAvailable())
            throw new IllegalStateException("Vpn manager not available.");
    }

    Handle onConnectionUpdating(Consumer<IVPNConnection> callback);

    Handle onConnectionRemoving(Consumer<IVPNConnection> callback);

    Handle onConnectionAdding(Runnable callback);

}
