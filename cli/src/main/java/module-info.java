open module com.jadaptive.nodal.vpn.client.cli {

    exports com.logonbox.vpn.client.cli;
    exports com.logonbox.vpn.client.cli.commands;
    requires transitive org.freedesktop.dbus;
    requires transitive com.jadaptive.nodal.vpn.client.dbus.client;
    requires transitive com.jadaptive.nodal.vpn.client.logging;
    requires transitive com.jadaptive.nodal.vpn.client.dbus.app;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires transitive info.picocli;
    requires com.sshtools.liftlib;
    requires jakarta.json;
    requires jul.to.slf4j;
    requires com.jadaptive.nodal.vpn.client.app;
    
}