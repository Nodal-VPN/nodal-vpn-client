import com.logonbox.vpn.client.common.logging.SimpleServiceProvider;
import com.logonbox.vpn.drivers.lib.DNSProvider;

import org.slf4j.spi.SLF4JServiceProvider;

open module com.logonbox.vpn.common.client {

    exports com.logonbox.vpn.client.common;
    exports com.logonbox.vpn.client.common.api;
    exports com.logonbox.vpn.client.common.lbapi;
    exports com.logonbox.vpn.client.common.logging;

    requires transitive com.logonbox.vpn.drivers.lib;
    requires org.slf4j;
    requires info.picocli;
    requires transitive java.net.http;
    requires static com.install4j.runtime;
    requires java.naming;
    requires java.prefs;
    requires java.xml;
    requires com.sshtools.liftlib;
    requires transitive jakarta.json;
    
    uses DNSProvider.Factory;
    provides SLF4JServiceProvider with SimpleServiceProvider;
}