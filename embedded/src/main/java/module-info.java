import com.logonbox.vpn.client.common.Api;
import com.logonbox.vpn.client.embedded.EmbedderApi;
import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

open module com.logonbox.vpn.client.embedded {
    exports com.logonbox.vpn.client.embedded;

    requires transitive com.logonbox.vpn.client.service;
    
    requires com.sshtools.liftlib;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.logonbox.vpn.client.common;
    uses PlatformServiceFactory;
    
    provides Api with EmbedderApi;
}