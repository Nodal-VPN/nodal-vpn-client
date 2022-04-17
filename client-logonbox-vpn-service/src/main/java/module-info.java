module com.logonbox.vpn.service {
	requires info.picocli;
	requires uk.co.bithatch.nativeimage.annotations;
	requires org.freedesktop.dbus;
	requires org.apache.commons.lang3;
	requires transitive java.prefs;
	requires transitive com.logonbox.vpn.common;
	requires com.fasterxml.jackson.databind;
	requires com.sshtools.forker.common;
	requires com.sun.jna;
	requires commons.ip.math;
	requires ini4j;
	requires com.sshtools.forker.client;
	requires transitive com.sshtools.forker.pipes;
	requires org.apache.commons.io;
	requires com.sshtools.forker.services;
}