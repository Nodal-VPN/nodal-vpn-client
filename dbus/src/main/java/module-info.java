open module com.logonbox.vpn.client.common.dbus {

    exports com.logonbox.vpn.client.common.dbus;

    requires transitive com.logonbox.vpn.drivers.lib;
    requires transitive com.logonbox.vpn.client.common;
    requires transitive org.freedesktop.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires java.naming;
    requires java.prefs;
    requires info.picocli;
}