package com.logonbox.vpn.client.embedded;

import com.logonbox.vpn.client.common.AbstractVpnManager;
import com.logonbox.vpn.client.common.api.IRemoteUI;
import com.logonbox.vpn.client.common.api.IVpn;

import java.util.Optional;

public class EmbeddedVpnManager extends AbstractVpnManager<EmbeddedVpnConnection> {
    
    private EmbeddedService service;

    EmbeddedVpnManager(EmbeddedService service) {
        this.service = service;
    }
    
    @Override
    public boolean isBackendAvailable() {
        return true;
    }

    @Override
    public Optional<IVpn<EmbeddedVpnConnection>> getVpn() {
        return service.getVpn();
    }

    @Override
    public void confirmExit() {
    }

    @Override
    public Optional<IRemoteUI> getUserInterface() {
        return Optional.empty();
    }

    @Override
    public void start() {        
    }

}
