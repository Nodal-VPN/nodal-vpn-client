import com.logonbox.vpn.client.gui.jfx.PowerMonitor;

open module com.logonbox.vpn.client.gui.jfx {
    exports com.logonbox.vpn.client.gui.jfx;
    
    requires transitive com.logonbox.vpn.common.client;
    requires java.desktop;
    requires transitive org.freedesktop.dbus;
    requires org.apache.commons.lang3;
    requires transitive com.sshtools.twoslices;
    requires transitive java.prefs;
    requires org.apache.commons.io;
    requires transitive javafx.controls;
    requires transitive javafx.web;
    requires transitive javafx.fxml;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome;
    requires transitive com.hypersocket.json;
    requires transitive org.slf4j;
    requires org.apache.commons.text;
    requires transitive jdk.jsobject;
    
    uses PowerMonitor;
}