import com.jadaptive.nodal.core.lib.DNSProvider;
import com.logonbox.vpn.client.common.Api;
import com.logonbox.vpn.client.common.PlatformUtilities;

open module com.logonbox.vpn.client.common {

    exports com.logonbox.vpn.client.common;
    exports com.logonbox.vpn.client.common.api;
    exports com.logonbox.vpn.client.common.lbapi;

    requires transitive com.jadaptive.nodal.core.lib;
    requires transitive java.net.http;
    requires java.naming;
    requires java.prefs;
    requires java.xml;
    requires transitive com.sshtools.jini.config;
    requires transitive com.sshtools.liftlib;
    requires transitive jakarta.json;
    requires transitive com.sshtools.jaul;
    requires static transitive com.install4j.runtime;
    
    requires static uk.co.bithatch.nativeimage.annotations;
    
    uses DNSProvider.Factory;
    uses Api;
    uses PlatformUtilities;
}