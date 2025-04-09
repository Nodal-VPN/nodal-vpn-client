open module com.jadaptive.nodal.vpn.client.dbus.app {

    exports com.logonbox.vpn.client.dbus.app;

    requires transitive com.jadaptive.nodal.core.lib;
    requires transitive com.jadaptive.nodal.vpn.client.common;
    requires transitive org.freedesktop.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires java.naming;
    requires java.prefs;
    requires transitive com.jadaptive.nodal.vpn.client.common.dbus;
    requires com.jadaptive.nodal.vpn.client.app;
    requires info.picocli;
    requires com.jadaptive.nodal.vpn.client.dbus.client;
}