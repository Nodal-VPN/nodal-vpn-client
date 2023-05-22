import com.logonbox.vpn.client.PlatformServiceFactory;
import com.logonbox.vpn.client.windows.WindowsPlatformServiceFactory;

open module com.logonbox.vpn.client.windows {
    exports com.logonbox.vpn.client.windows;
    exports com.logonbox.vpn.client.windows.service;
    
    requires transitive com.logonbox.vpn.client;
    requires com.sun.jna.platform;
    requires transitive com.sshtools.forker.services;
    requires transitive java.prefs;
    requires org.apache.commons.lang3;
    requires com.sshtools.forker.pipes;
    
    provides PlatformServiceFactory with WindowsPlatformServiceFactory;
}