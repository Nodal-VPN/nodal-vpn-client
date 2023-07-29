open module com.logonbox.vpn.common.client {

    exports com.logonbox.vpn.common.client;
    exports com.logonbox.vpn.common.client.api;
    exports com.logonbox.vpn.common.client.dbus;
    
    requires org.slf4j;
    requires info.picocli;
    requires transitive java.net.http;
    requires org.apache.commons.lang3;
    requires org.freedesktop.dbus;
    requires static com.install4j.runtime;
    requires com.hypersocket.json;
    requires transitive com.fasterxml.jackson.databind;
    requires java.naming;
    requires java.prefs;
}