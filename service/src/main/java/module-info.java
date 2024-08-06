import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

module com.logonbox.vpn.client.service {
    exports com.logonbox.vpn.client;
    exports com.logonbox.vpn.client.ini;
    exports com.logonbox.vpn.client.service;
    
    requires transitive com.logonbox.vpn.client.common;
    requires org.slf4j;
    requires java.net.http;
    
    requires com.github.jgonian.ipmath;
    requires com.sshtools.jini;
    requires com.sshtools.jini.config;
    requires com.sshtools.liftlib;
    
    requires static uk.co.bithatch.nativeimage.annotations;
    
    uses PlatformServiceFactory;
}