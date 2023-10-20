import com.logonbox.vpn.drivers.lib.DNSProvider;

open module com.logonbox.vpn.common.client {

    exports com.logonbox.vpn.client.common;
    exports com.logonbox.vpn.client.common.api;

    requires transitive com.logonbox.vpn.drivers.lib;
    requires org.slf4j;
    requires info.picocli;
    requires transitive java.net.http;
    requires org.apache.commons.lang3;
    requires static com.install4j.runtime;
    requires transitive com.hypersocket.json;
    requires transitive com.fasterxml.jackson.databind;
    requires java.naming;
    requires java.prefs;
    requires java.xml;
    
    uses DNSProvider.Factory;
}