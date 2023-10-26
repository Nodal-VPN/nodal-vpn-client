import com.logonbox.vpn.drivers.lib.DNSProvider;

open module com.logonbox.vpn.client.app {

    exports com.logonbox.vpn.client.app;

    requires transitive com.logonbox.vpn.client.common;
    requires info.picocli;
    requires static com.install4j.runtime;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.sshtools.liftlib;
    
    uses DNSProvider.Factory;
}