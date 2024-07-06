
open module com.logonbox.vpn.client.desktop {
    exports com.logonbox.vpn.client.desktop;
    requires transitive com.logonbox.vpn.client.gui.jfx;
    requires transitive com.logonbox.vpn.client.dbus.app;
    requires transitive com.logonbox.vpn.client.dbus.client;
    requires transitive com.sshtools.jajafx;
    requires com.install4j.runtime;
    requires org.scenicview.scenicview;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.logonbox.vpn.client.logging;
    requires com.logonbox.vpn.client.app;
    requires com.goxr3plus.fxborderlessscene;
    requires com.jthemedetector;
    requires java.desktop;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
} 