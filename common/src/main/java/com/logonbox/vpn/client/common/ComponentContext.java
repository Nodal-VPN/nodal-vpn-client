package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.net.CookieStore;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

public interface ComponentContext<CONX extends IVpnConnection> {

    PromptingCertManager getCertManager();

    CookieStore getCookieStore();

    ScheduledExecutorService getScheduler();

    void shutdown(boolean restart);

    Optional<IVpn<CONX>> getVpn();
    
    LoggingConfig getLogging();
}
