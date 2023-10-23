open module com.logonbox.vpn.client.common.dbus {

    exports com.logonbox.vpn.client.common.dbus;

    requires transitive com.logonbox.vpn.drivers.lib;
    requires transitive com.logonbox.vpn.common.client;
    requires transitive org.freedesktop.dbus;
    requires java.naming;
    requires java.prefs;
    requires info.picocli;
}