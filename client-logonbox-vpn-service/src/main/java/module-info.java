import com.logonbox.vpn.client.wireguard.PlatformService;

module com.logonbox.vpn.client {
    exports com.logonbox.vpn.client;
    exports com.logonbox.vpn.client.dbus;
    exports com.logonbox.vpn.client.ini;
    exports com.logonbox.vpn.client.service;
    exports com.logonbox.vpn.client.wireguard;
    
    requires transitive com.logonbox.vpn.common.client;
    requires org.slf4j;
    requires org.apache.commons.lang3;
    requires transitive org.freedesktop.dbus;
    requires java.prefs;
    requires java.net.http;
    
    requires commons.ip.math;
    requires com.sshtools.jini;
    requires com.hypersocket.json;
    
    uses PlatformService;
}