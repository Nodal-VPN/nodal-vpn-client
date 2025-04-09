
open module com.jadaptive.nodal.vpn.client.desktop {
    exports com.logonbox.vpn.client.desktop;
    requires transitive com.jadaptive.nodal.vpn.client.gui.jfx;
    requires transitive com.jadaptive.nodal.vpn.client.dbus.app;
    requires transitive com.jadaptive.nodal.vpn.client.dbus.client;
    requires transitive com.sshtools.jajafx;
    requires com.install4j.runtime;
    requires org.scenicview.scenicview;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.jadaptive.nodal.vpn.client.logging;
    requires com.jadaptive.nodal.vpn.client.app;
    requires com.goxr3plus.fxborderlessscene;
    requires com.jthemedetector;
    requires java.desktop;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
} 