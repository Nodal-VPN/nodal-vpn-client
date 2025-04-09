
open module com.jadaptive.nodal.vpn.client.gui.jfx {
    exports com.logonbox.vpn.client.gui.jfx;
    
    requires transitive javafx.controls;
    requires transitive javafx.web;
    requires transitive javafx.fxml;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome;
    requires transitive org.slf4j;
    requires transitive jdk.jsobject;
    requires com.sshtools.liftlib;
    requires jakarta.json;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires transitive com.jadaptive.nodal.vpn.client.common;
    requires transitive com.sshtools.jini.config;
    
}