package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.util.Optional;

public interface AppContext<CONX extends IVpnConnection> extends ComponentContext<CONX> {
    
    @Override
    default Optional<IVpn<CONX>> getVpn() {
        return getVpnManager().getVpn();
    }

    VpnManager<CONX> getVpnManager();

    String getVersion();

    boolean isConsole();

    UpdateService getUpdateService();
    
    boolean isInteractive();

    String getEffectiveUser();
}
