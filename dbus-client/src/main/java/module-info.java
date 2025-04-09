open module com.jadaptive.nodal.vpn.client.dbus.client {

    exports com.logonbox.vpn.client.dbus.client;

    requires transitive com.jadaptive.nodal.core.lib;
    requires transitive com.jadaptive.nodal.vpn.client.common;
    requires transitive org.freedesktop.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires transitive com.jadaptive.nodal.vpn.client.common.dbus;
}