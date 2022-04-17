module com.logonbox.vpn.gui.jfx {
	requires info.picocli;
	requires uk.co.bithatch.nativeimage.annotations;
	requires com.logonbox.vpn.common;
	requires org.slf4j;
	requires org.freedesktop.dbus;
	requires org.apache.commons.lang3;
	requires javafx.web;
	requires javafx.base;
	requires org.apache.commons.io;
	requires org.apache.commons.text;
	requires org.kordamp.ikonli.javafx;
	requires javafx.fxml;
	requires jdk.jsobject;
	requires com.goxr3plus.fxborderlessscene;
	opens com.logonbox.vpn.client.gui.jfx;
}