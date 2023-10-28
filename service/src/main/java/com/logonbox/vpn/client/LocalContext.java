package com.logonbox.vpn.client;

import com.logonbox.vpn.client.common.ComponentContext;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.service.ClientService;
import com.logonbox.vpn.drivers.lib.PlatformService;

import java.io.Closeable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public interface LocalContext<CONX extends IVpnConnection> extends Closeable, ComponentContext<CONX> {

    PlatformService<?> getPlatformService();

    ClientService<CONX> getClientService();

    SSLContext getSSLContext();

    SSLParameters getSSLParameters();

    @Override
    void close();

    void fireAuthorize(Connection connection, String authorizeUri);

    void fireConnectionUpdated(Connection connection);

    void fireConnectionUpdating(Connection connection);

    void fireTemporarilyOffline(Connection connection, Exception reason);

    void fireConnected(Connection connection);

    void fireConnectionAdding();

    void connectionAdded(Connection connection);

    void fireConnectionRemoving(Connection connection);

    void connectionRemoved(Connection connection);

    void fireDisconnecting(Connection connection, String reason);

    void fireDisconnected(Connection connection, String reason);

    void fireConnecting(Connection connection);

    void fireFailed(Connection connection, String message, String cause, String trace);

    void promptForCertificate(PromptType alertType, String title, String content, String key, String hostname,
            String message);
    
    void fireExit();

}
