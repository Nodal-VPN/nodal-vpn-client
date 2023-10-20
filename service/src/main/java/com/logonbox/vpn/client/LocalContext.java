package com.logonbox.vpn.client;

import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.service.ClientService;
import com.logonbox.vpn.drivers.lib.PlatformService;

import org.slf4j.event.Level;

import java.io.Closeable;
import java.net.CookieStore;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public interface LocalContext extends Closeable {

    PlatformService<?> getPlatformService();

    ClientService getClientService();

    void shutdown(boolean restart);

    SSLContext getSSLContext();

    PromptingCertManager getCertManager();

    SSLParameters getSSLParameters();

    CookieStore getCookieStore();

    ScheduledExecutorService getQueue();

    Level getDefaultLevel();

    void setLevel(Level level);

    VpnManager getVPNManager();

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
