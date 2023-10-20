import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

open module com.logonbox.vpn.client.desktop.service {
    exports com.logonbox.vpn.client.desktop.service;
    
    requires transitive com.logonbox.vpn.client.service;
    requires transitive com.logonbox.vpn.client.common.dbus;
    requires org.apache.commons.lang3;
    requires info.picocli;
    requires reload4j;
    requires slf4j.reload4j;
    requires com.sshtools.liftlib;
    requires org.freedesktop.dbus;
    
    uses PlatformServiceFactory; 
}