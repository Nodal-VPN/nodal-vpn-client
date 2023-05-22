import com.logonbox.vpn.client.PlatformServiceFactory;

open module com.logonbox.vpn.client.desktop.service {
    exports com.logonbox.vpn.client.desktop.service;
    
    requires transitive com.logonbox.vpn.client;
    requires transitive com.sshtools.forker.client;
    requires org.apache.commons.lang3;
    requires info.picocli;
    requires reload4j;
    requires slf4j.reload4j;
    
    uses PlatformServiceFactory;
}