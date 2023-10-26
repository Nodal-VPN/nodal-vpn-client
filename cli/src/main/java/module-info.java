open module com.logonbox.vpn.client.cli {

    exports com.logonbox.vpn.client.cli;
    exports com.logonbox.vpn.client.cli.commands;
    requires transitive org.freedesktop.dbus;
    requires transitive com.logonbox.vpn.client.common;
    requires transitive com.logonbox.vpn.client.common.dbus;
    requires transitive com.logonbox.vpn.client.logging;
    requires transitive com.logonbox.vpn.client.dbus.app;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires info.picocli;
    requires com.sshtools.liftlib;
    requires jakarta.json;
    
}