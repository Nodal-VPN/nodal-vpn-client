import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

open module com.logonbox.vpn.client.desktop.service {
    exports com.logonbox.vpn.client.desktop.service;
    
    requires transitive com.logonbox.vpn.client.service;
    requires transitive com.logonbox.vpn.client.common.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires info.picocli;
    requires com.sshtools.liftlib;
    requires org.freedesktop.dbus;
    requires jul.to.slf4j;
    requires java.logging;
    
    uses PlatformServiceFactory; 
}