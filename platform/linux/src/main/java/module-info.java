import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.linux.LinuxPlatformUtilities;

open module com.logonbox.vpn.client.linux {
    exports com.logonbox.vpn.client.linux;

    requires transitive com.logonbox.vpn.client.common;
    
    provides PlatformUtilities with LinuxPlatformUtilities;
}