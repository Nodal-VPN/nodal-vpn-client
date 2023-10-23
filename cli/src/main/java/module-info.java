open module com.logonbox.vpn.client.cli {

    exports com.logonbox.vpn.client.cli;
    exports com.logonbox.vpn.client.cli.commands;
    requires transitive org.freedesktop.dbus;
    requires transitive com.logonbox.vpn.common.client;
    requires transitive com.logonbox.vpn.client.common.dbus;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires info.picocli;
    requires com.sshtools.liftlib;
    requires jakarta.json;
    requires com.logonbox.vpn.client.logging;
    
}