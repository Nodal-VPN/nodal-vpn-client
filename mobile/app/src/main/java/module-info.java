import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

open module com.logonbox.vpn.client.mobile {
    exports com.logonbox.vpn.client.mobile;

    requires transitive com.logonbox.vpn.client.gui.jfx;
    requires transitive com.logonbox.vpn.client.embedded;
    requires transitive com.logonbox.vpn.client.app;
//    requires com.logonbox.vpn.client.attach.lbvpn; 
    
//    requires transitive org.kordamp.ikonli.javafx;
//    requires org.kordamp.ikonli.fontawesome;
    requires com.gluonhq.charm.glisten;
    requires com.gluonhq.attach.display;
//    requires com.gluonhq.attach.lifecycle;
//    requires com.gluonhq.attach.statusbar;
    requires com.gluonhq.attach.storage;
    requires com.gluonhq.attach.util;
    requires jul.to.slf4j;
    requires info.picocli;
    requires com.sshtools.liftlib;
    requires com.logonbox.vpn.client.logging;
    requires static uk.co.bithatch.nativeimage.annotations;
    uses PlatformServiceFactory;
}