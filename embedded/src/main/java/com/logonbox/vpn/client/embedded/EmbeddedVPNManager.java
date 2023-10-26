package com.logonbox.vpn.client.embedded;

import com.logonbox.vpn.client.common.AbstractVpnManager;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.UpdateService;
import com.logonbox.vpn.client.common.api.IVPN;

import java.net.CookieStore;
import java.util.List;
import java.util.Optional;

public class EmbeddedVPNManager extends AbstractVpnManager<EmbeddedVPNConnection> {

    @Override
    public boolean isBusAvailable() {
        return true;
    }

    @Override
    public Optional<IVPN<EmbeddedVPNConnection>> getVPN() {
        // TODO Auto-generated method stub
        return Optional.empty();
    }

    @Override
    public String getVersion() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public void exit() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public UpdateService getUpdateService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<EmbeddedVPNConnection> getVPNConnections() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public EmbeddedVPNConnection getVPNConnection(long id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public PromptingCertManager getCertManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CookieStore getCookieStore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void confirmExit() {
    }

}
