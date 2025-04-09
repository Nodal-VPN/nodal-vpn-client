import com.jadaptive.nodal.core.lib.DNSProvider;

open module com.jadaptive.nodal.vpn.client.app {

    exports com.logonbox.vpn.client.app;

    requires transitive com.jadaptive.nodal.vpn.client.common;
    requires info.picocli;
    requires static com.install4j.runtime;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.sshtools.liftlib;
    requires java.logging;
    requires com.jadaptive.nodal.vpn.client.logging;
    requires jul.to.slf4j;
    
    uses DNSProvider.Factory;
}