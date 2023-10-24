
open module com.logonbox.vpn.client.desktop {
    exports com.logonbox.vpn.client.desktop;
    requires transitive com.logonbox.vpn.common.client;
    requires transitive com.logonbox.vpn.client.gui.jfx;
    requires transitive com.logonbox.vpn.client.common.dbus;
    requires com.goxr3plus.fxborderlessscene;
    requires com.jthemedetector;
//    requires java.desktop;
    requires org.kordamp.ikonli.fontawesome;
    requires info.picocli;
    requires com.install4j.runtime;
    requires com.sshtools.liftlib;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.logonbox.vpn.client.logging;
} 