import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.PlatformServiceFactory;

open module com.jadaptive.nodal.vpn.client.desktop.service {
    exports com.logonbox.vpn.client.desktop.service;
    
    requires transitive com.jadaptive.nodal.vpn.client.service;
    requires transitive com.jadaptive.nodal.vpn.client.app;
    requires transitive com.jadaptive.nodal.vpn.client.common.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires info.picocli;
    requires com.sshtools.liftlib;
    requires org.freedesktop.dbus;
    requires jul.to.slf4j;
    requires java.logging;
    requires com.jadaptive.nodal.vpn.client.logging;
    requires com.sshtools.jadbus.lib;
    
    uses PlatformServiceFactory;
    uses DNSProvider.Factory;
}