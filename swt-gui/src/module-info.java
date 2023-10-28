module com.logonbox.vpn.tray {
	requires info.picocli;
	requires static uk.co.bithatch.nativeimage.annotations;
	requires com.logonbox.vpn.client.common.dbus;
    requires com.logonbox.vpn.client.logging;
	requires org.slf4j;
	requires org.freedesktop.dbus;
	requires transitive static org.eclipse.swt.gtk.linux.x86_64;
	requires com.sshtools.twoslices;
    requires java.prefs;
    requires com.sshtools.liftlib;
	opens com.logonbox.vpn.client.tray;
}