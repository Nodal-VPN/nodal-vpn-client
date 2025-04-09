import com.jadaptive.nodal.core.lib.PlatformServiceFactory;
import com.logonbox.vpn.client.common.Api;
import com.logonbox.vpn.client.embedded.EmbedderApi;

open module com.logonbox.vpn.client.embedded {
    exports com.logonbox.vpn.client.embedded;

    requires transitive com.logonbox.vpn.client.service;
    
    requires com.sshtools.liftlib;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.logonbox.vpn.client.common;
    uses PlatformServiceFactory;
    
    provides Api with EmbedderApi;
}