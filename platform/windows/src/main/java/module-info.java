import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.windows.WindowsPlatformUtilities;

open module com.jadaptive.nodal.vpn.client.windows {
    exports com.logonbox.vpn.client.windows;

    requires transitive com.jadaptive.nodal.vpn.client.common;
    requires com.sun.jna.platform;
    
    provides PlatformUtilities with WindowsPlatformUtilities;
}