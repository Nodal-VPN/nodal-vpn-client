import com.logonbox.vpn.client.PlatformServiceFactory;
import com.logonbox.vpn.client.macos.MacOsPlatformServiceFactory;

open module com.logonbox.vpn.client.macos {
    exports com.logonbox.vpn.client.macos;
    
    requires transitive com.logonbox.vpn.client;
    requires transitive java.prefs;
    requires transitive com.sshtools.forker.client;
    requires org.apache.commons.lang3;
    requires org.apache.commons.io;
    requires commons.ip.math;
    
    provides PlatformServiceFactory with MacOsPlatformServiceFactory;
}