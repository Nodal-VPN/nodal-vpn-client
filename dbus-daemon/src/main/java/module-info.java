open module com.logonbox.vpn.client.dbus.daemon {
    exports com.logonbox.vpn.client.dbus.daemon;
    requires org.slf4j;
    requires java.prefs;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires transitive org.freedesktop.dbus;
    requires com.logonbox.vpn.client.logging;
    requires info.picocli;
    requires jul.to.slf4j;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    
}