import com.logonbox.vpn.client.PlatformServiceFactory;
import com.logonbox.vpn.client.linux.LinuxPlatformServiceFactory;
import com.logonbox.vpn.client.linux.LinuxPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.PlatformService;

open module com.logonbox.vpn.client.linux {
    exports com.logonbox.vpn.client.linux;
    
    requires transitive com.logonbox.vpn.client;
    requires transitive java.prefs;
    requires transitive com.sshtools.forker.client;
    requires org.apache.commons.lang3;
    requires org.apache.commons.io;
    requires commons.ip.math;
    
    provides PlatformServiceFactory with LinuxPlatformServiceFactory;
}