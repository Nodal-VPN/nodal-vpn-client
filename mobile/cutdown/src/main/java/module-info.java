import com.jadaptive.nodal.core.lib.PlatformServiceFactory;

open module com.logonbox.vpn.client.mobile {
    exports com.logonbox.vpn.client.cutdown;

    requires transitive com.logonbox.vpn.client.gui.jfx;
    requires transitive com.logonbox.vpn.client.embedded;
    requires transitive com.logonbox.vpn.client.app;
    requires jul.to.slf4j;
    requires info.picocli;
    requires com.sshtools.liftlib;
    requires com.logonbox.vpn.client.logging;
    requires static uk.co.bithatch.nativeimage.annotations;
    uses PlatformServiceFactory;
}