module com.logonbox.vpn.tray {
	requires info.picocli;
	requires uk.co.bithatch.nativeimage.annotations;
	requires com.logonbox.vpn.common;
	requires org.slf4j;
	requires org.freedesktop.dbus;
	requires org.apache.commons.lang3;
	requires transitive static org.eclipse.swt.gtk.linux.x86_64;
	requires com.sshtools.twoslices;
	opens com.logonbox.vpn.client.tray;
}