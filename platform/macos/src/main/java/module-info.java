import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.macos.MacOsPlatformUtilities;

open module com.jadaptive.nodal.vpn.client.macos {
    exports com.logonbox.vpn.client.macos;

    requires transitive com.jadaptive.nodal.vpn.client.common;
    
    provides PlatformUtilities with MacOsPlatformUtilities;
}