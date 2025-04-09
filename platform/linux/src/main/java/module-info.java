import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.linux.LinuxPlatformUtilities;

open module com.jadaptive.nodal.vpn.client.linux {
    exports com.logonbox.vpn.client.linux;

    requires transitive com.jadaptive.nodal.vpn.client.common;
    
    provides PlatformUtilities with LinuxPlatformUtilities;
}