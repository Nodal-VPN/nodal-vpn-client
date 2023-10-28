
open module com.logonbox.vpn.client.desktop {
    exports com.logonbox.vpn.client.desktop;
    requires transitive com.logonbox.vpn.client.gui.jfx;
    requires transitive com.logonbox.vpn.client.dbus.app;
    requires transitive com.logonbox.vpn.client.dbus.client;
    requires com.goxr3plus.fxborderlessscene;
    requires com.jthemedetector;
    requires info.picocli;
    requires com.install4j.runtime;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.logonbox.vpn.client.logging;
    requires com.logonbox.vpn.client.app;
} 