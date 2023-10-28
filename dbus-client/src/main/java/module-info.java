open module com.logonbox.vpn.client.dbus.client {

    exports com.logonbox.vpn.client.dbus.client;

    requires transitive com.logonbox.vpn.drivers.lib;
    requires transitive com.logonbox.vpn.client.common;
    requires transitive org.freedesktop.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires transitive com.logonbox.vpn.client.common.dbus;
}