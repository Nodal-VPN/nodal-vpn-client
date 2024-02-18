package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.sshtools.jaul.UpdateService;
import com.sshtools.jaul.UpdateableAppContext;

import java.util.Optional;

public interface AppContext<CONX extends IVpnConnection> extends ComponentContext<CONX>, UpdateableAppContext {
    
    @Override
    default Optional<IVpn<CONX>> getVpn() {
        return getVpnManager().getVpn();
    }

    VpnManager<CONX> getVpnManager();

    boolean isConsole();

    UpdateService getUpdateService();
    
    boolean isInteractive();

    String getEffectiveUser();
}
