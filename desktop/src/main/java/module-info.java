
open module com.logonbox.vpn.client.desktop {
    exports com.logonbox.vpn.client.desktop;
    requires transitive com.logonbox.vpn.common.client;
    requires transitive com.logonbox.vpn.client.gui.jfx;
    requires transitive com.logonbox.vpn.client.common.dbus;
    requires com.goxr3plus.fxborderlessscene;
    requires com.jthemedetector;
    requires java.desktop;
    requires org.apache.commons.lang3;
    requires transitive slf4j.reload4j;
    requires reload4j;
    requires org.kordamp.ikonli.fontawesome;
    requires SystemTray;
    requires org.apache.commons.io;
    requires info.picocli;
    requires com.install4j.runtime;
} 