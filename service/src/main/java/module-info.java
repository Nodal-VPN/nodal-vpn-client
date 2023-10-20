import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

module com.logonbox.vpn.client.service {
    exports com.logonbox.vpn.client;
    exports com.logonbox.vpn.client.ini;
    exports com.logonbox.vpn.client.service;
    
    requires transitive com.logonbox.vpn.common.client;
    requires org.slf4j;
    requires org.apache.commons.lang3;
    requires java.prefs;
    requires java.net.http;
    
    requires commons.ip.math;
    requires com.sshtools.jini;
    requires com.hypersocket.json;
    
    uses PlatformServiceFactory;
}