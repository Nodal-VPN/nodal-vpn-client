import com.jadaptive.nodal.core.lib.PlatformServiceFactory;

module com.jadaptive.nodal.vpn.client.service {
    exports com.logonbox.vpn.client;
    exports com.logonbox.vpn.client.ini;
    exports com.logonbox.vpn.client.service;
    
    requires transitive com.jadaptive.nodal.vpn.client.common;
    requires org.slf4j;
    requires java.net.http;
    
    requires com.sshtools.jini;
    requires com.sshtools.jini.config;
    requires com.sshtools.liftlib;
    
    requires static uk.co.bithatch.nativeimage.annotations;
    
    uses PlatformServiceFactory;
}