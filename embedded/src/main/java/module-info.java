import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

open module com.logonbox.vpn.client.embedded {
    exports com.logonbox.vpn.client.embedded;

    requires transitive com.logonbox.vpn.client.service;
    
    requires info.picocli;
    requires com.sshtools.liftlib;
    requires com.logonbox.vpn.client.logging;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.logonbox.vpn.client.common;
    uses PlatformServiceFactory;
}