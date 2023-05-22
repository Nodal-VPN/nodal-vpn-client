open module com.logonbox.vpn.client.cli {

    exports com.logonbox.vpn.client.cli;
    exports com.logonbox.vpn.client.cli.commands;
    requires org.apache.commons.lang3;
    requires transitive org.freedesktop.dbus;
    requires com.hypersocket.json;
    requires transitive com.logonbox.vpn.common.client;
    requires info.picocli;
    requires reload4j;
    requires slf4j.reload4j;
    
}