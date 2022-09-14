module com.logonbox.vpn.common {
	requires uk.co.bithatch.nativeimage.annotations;
	requires info.picocli;
	requires org.slf4j;
	requires transitive org.freedesktop.dbus;
	requires org.apache.commons.lang3;
	requires transitive java.net.http;
	requires com.fasterxml.jackson.annotation;
	requires com.fasterxml.jackson.core;
	requires transitive com.fasterxml.jackson.databind;
	requires transitive com.hypersocket.json;
	requires transitive java.prefs;
	requires com.install4j.runtime;
	requires transitive java.naming;
	requires transitive java.logging;
	requires transitive com.sun.jna.platform;
	exports com.logonbox.vpn.common.client;
	exports com.logonbox.vpn.common.client.api;
	exports com.logonbox.vpn.common.client.dbus;
}