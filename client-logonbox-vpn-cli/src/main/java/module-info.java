module com.logonbox.vpn.cli {
	requires info.picocli;
	requires uk.co.bithatch.nativeimage.annotations;
	requires org.freedesktop.dbus;
	requires org.apache.commons.lang3;
	requires transitive java.prefs;
	requires transitive com.logonbox.vpn.common;
	requires com.fasterxml.jackson.databind;
	opens com.logonbox.vpn.client.cli;
}