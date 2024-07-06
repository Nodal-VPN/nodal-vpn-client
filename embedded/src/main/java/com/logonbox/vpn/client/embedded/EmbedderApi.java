package com.logonbox.vpn.client.embedded;

import com.logonbox.vpn.client.common.Api;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.function.Predicate;

import uk.co.bithatch.nativeimage.annotations.Resource;

@Resource
public class EmbedderApi implements Api<EmbeddedVpnConnection> {

    public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(EmbedderApi.class.getName());
    private final EmbeddedService srv;
    
    public EmbedderApi() throws Exception {
        this(pf -> true);
    }
    
    public EmbedderApi(Predicate<PlatformServiceFactory> platformServiceFilter) throws Exception {
        srv = new EmbeddedService(BUNDLE, platformServiceFilter);
    }
    
    @Override
    public VpnManager<EmbeddedVpnConnection> manager() {
        return srv.getVpnManager();
    }

    @Override
    public IVpn<EmbeddedVpnConnection> vpn() {
        return srv.getVpnManager().getVpnOrFail();
    }

    public static void main(String[] args)  throws Exception {
        var embedder = new EmbedderApi((pf) -> true);
        Arrays.asList(embedder.vpn().getConnections()).forEach(vpn -> {
            System.out.println(vpn.getDisplayName());
        });
    }
}
