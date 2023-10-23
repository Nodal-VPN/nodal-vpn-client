open module com.logonbox.vpn.client.cli {

    exports com.logonbox.vpn.client.cli;
    exports com.logonbox.vpn.client.cli.commands;
    requires transitive org.freedesktop.dbus;
    requires transitive com.logonbox.vpn.common.client;
    requires transitive com.logonbox.vpn.client.common.dbus;
    requires info.picocli;
    requires reload4j;
    requires slf4j.reload4j;
    requires com.sshtools.liftlib;
    requires jakarta.json;
    
}