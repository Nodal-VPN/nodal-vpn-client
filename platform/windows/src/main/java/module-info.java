import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.windows.WindowsPlatformUtilities;

open module com.logonbox.vpn.client.windows {
    exports com.logonbox.vpn.client.windows;

    requires transitive com.logonbox.vpn.client.common;
    requires com.sun.jna.platform;
    
    provides PlatformUtilities with WindowsPlatformUtilities;
}