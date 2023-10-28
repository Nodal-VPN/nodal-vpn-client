import com.logonbox.vpn.client.logging.SimpleServiceProvider;

import org.slf4j.spi.SLF4JServiceProvider;

open module com.logonbox.vpn.client.logging {

    exports com.logonbox.vpn.client.logging;
    requires transitive org.slf4j;
    requires transitive java.logging;

    provides SLF4JServiceProvider with SimpleServiceProvider;
}