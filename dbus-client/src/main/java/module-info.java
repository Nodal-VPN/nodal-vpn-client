open module com.logonbox.vpn.client.dbus.client {

    exports com.logonbox.vpn.client.dbus.client;

    requires transitive com.logonbox.vpn.drivers.lib;
    requires transitive com.logonbox.vpn.client.common;
    requires transitive org.freedesktop.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires java.naming;
    requires java.prefs;
    requires transitive com.logonbox.vpn.client.common.dbus;
    requires com.logonbox.vpn.client.app;
    requires info.picocli;
}