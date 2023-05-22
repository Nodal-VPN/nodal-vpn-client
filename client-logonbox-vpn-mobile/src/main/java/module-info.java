
open module com.logonbox.vpn.client.mobile {
    exports com.logonbox.vpn.client.mobile;
    requires com.sshtools.twoslices;
    requires transitive com.logonbox.vpn.client.gui.jfx;
    requires transitive org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome;
    requires org.apache.commons.lang3;
    requires info.picocli;
    requires com.gluonhq.charm.glisten;
    requires com.gluonhq.attach.display;
    requires com.gluonhq.attach.lifecycle;
    requires com.gluonhq.attach.statusbar;
    requires com.gluonhq.attach.storage;
    requires com.gluonhq.attach.util;
    requires com.logonbox.vpn.client;
    requires com.logonbox.vpn.client.attach.wireguard;
    
}