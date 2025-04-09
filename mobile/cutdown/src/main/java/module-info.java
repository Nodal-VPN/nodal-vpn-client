import com.jadaptive.nodal.core.lib.PlatformServiceFactory;

open module com.jadaptive.nodal.vpn.client.mobile {
    exports com.logonbox.vpn.client.cutdown;

    requires transitive com.jadaptive.nodal.vpn.client.gui.jfx;
    requires transitive com.jadaptive.nodal.vpn.client.embedded;
    requires transitive com.jadaptive.nodal.vpn.client.app;
    requires jul.to.slf4j;
    requires info.picocli;
    requires com.sshtools.liftlib;
    requires com.jadaptive.nodal.vpn.client.logging;
    requires static uk.co.bithatch.nativeimage.annotations;
    uses PlatformServiceFactory;
}