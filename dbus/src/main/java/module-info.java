open module com.jadaptive.nodal.vpn.client.common.dbus {

    exports com.logonbox.vpn.client.common.dbus;

    requires transitive com.jadaptive.nodal.core.lib;
    requires transitive com.jadaptive.nodal.vpn.client.common;
    requires transitive org.freedesktop.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires java.naming;
    requires java.prefs;
    requires com.sshtools.jadbus.lib;
}