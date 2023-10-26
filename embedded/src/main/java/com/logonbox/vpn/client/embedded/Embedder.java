package com.logonbox.vpn.client.embedded;

import com.logonbox.vpn.client.common.api.IVPN;

import java.util.ResourceBundle;

import uk.co.bithatch.nativeimage.annotations.Resource;

@Resource
public class Embedder {

    public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(Embedder.class.getName());
    private final IVPN<EmbeddedVPNConnection> vpn;
    private final EmbeddedVPNManager mgr;
    
    public Embedder() throws Exception {
        mgr = new EmbeddedVPNManager();
        var srv = new EmbeddedService(mgr, BUNDLE, this::vpn, (pf) -> true);
        vpn = new EmbeddedVPN(srv, mgr);
    }
    
    public EmbeddedVPNManager manager() {
        return mgr;
    }
    
    public IVPN<EmbeddedVPNConnection> vpn() {
        return vpn;
    }

    public static void main(String[] args)  throws Exception {
        var embedder = new Embedder();
        embedder.vpn().getConnections().forEach(vpn -> {
            System.out.println(vpn.getDisplayName());
        });
    }
}
