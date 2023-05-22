package com.logonbox.vpn.client.attach.wireguard.impl;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.PlatformServiceFactory;
import com.logonbox.vpn.client.attach.wireguard.MobilePlatformService;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.logonbox.vpn.common.client.StatusDetail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Desktop implementation of a {@link PlatformService}, responsible for querying
 * the state of existing VPN connections, creating new ones and other platform
 * specific actions. Delegates to the same modules used in the full desktop
 * VPN client build.
 */
public class DesktopPlatformService implements MobilePlatformService {

    /**
     * Constructor
     */
    private PlatformService<?> delegate;

    /**
     * Constructor
     */
    protected DesktopPlatformService() {
        delegate = ServiceLoader.load(PlatformServiceFactory.class).stream().filter(p -> p.get().isSupported()).findFirst().orElseThrow(()-> new IllegalStateException("No support desktop platform providers on modulepath")).get().createPlatformService();
    }

    @Override
    public void openToEveryone(Path path) throws IOException {
        delegate.openToEveryone(path);
    }

    @Override
    public void restrictToUser(Path path) throws IOException {
        delegate.restrictToUser(path);        
    }

    @Override
    public String[] getMissingPackages() {
        return delegate.getMissingPackages();
    }

    @Override
    public String pubkey(String privateKey) {
        return delegate.pubkey(privateKey);
    }

    @Override
    public Collection<VPNSession> start(LocalContext ctx) {
        return delegate.start(ctx);
    }

    @Override
    public VirtualInetAddress<?> connect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
        return delegate.connect(logonBoxVPNSession, configuration);
    }

    @Override
    public VirtualInetAddress<?> getByPublicKey(String publicKey) {
        return delegate.getByPublicKey(publicKey);
    }

    @Override
    public boolean isAlive(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
        return delegate.isAlive(logonBoxVPNSession, configuration)
                ;
    }

    @Override
    public void disconnect(VPNSession session) throws IOException {
        delegate.disconnect(session);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<VirtualInetAddress<?>> ips(boolean wireguardOnly) {
        return (List<VirtualInetAddress<?>>) delegate.ips(wireguardOnly);
    }

    @Override
    public StatusDetail status(String interfaceName) throws IOException {
        return delegate.status(interfaceName);
    }

    @Override
    public void runHook(VPNSession session, String hookScript) throws IOException {
        delegate.runHook(session, hookScript);
    }

    @Override
    public DNSIntegrationMethod dnsMethod() {
        return delegate.dnsMethod();
    }

    
}
