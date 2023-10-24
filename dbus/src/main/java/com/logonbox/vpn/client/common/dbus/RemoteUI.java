package com.logonbox.vpn.client.common.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

import uk.co.bithatch.nativeimage.annotations.Proxy;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@DBusInterfaceName("com.logonbox.vpn.RemoteUI")
@Proxy
@Reflectable
@TypeReflect(methods = true, classes = true)
public interface RemoteUI extends DBusInterface {

	String OBJECT_PATH = "/com/logonbox/vpn/RemoteUI";
	String BUS_NAME = "com.logonbox.vpn.RemoteUI";

	void open();

	void options();

	void confirmExit();

	void exitApp();

	@Reflectable
	@TypeReflect(methods = true, constructors = true)
	public class ConfirmedExit extends DBusSignal {
		public ConfirmedExit(String path) throws DBusException {
			super(path);
		}
	}
}