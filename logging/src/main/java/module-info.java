import com.logonbox.vpn.client.logging.SimpleServiceProvider;

import org.slf4j.spi.SLF4JServiceProvider;

open module com.logonbox.vpn.client.logging {

    exports com.logonbox.vpn.client.logging;
    requires org.slf4j;

    provides SLF4JServiceProvider with SimpleServiceProvider;
}