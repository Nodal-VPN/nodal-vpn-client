package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.api.IRemoteUI;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
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
public interface RemoteUI extends DBusInterface, IRemoteUI {

	String OBJECT_PATH = "/com/logonbox/vpn/RemoteUI";
	String BUS_NAME = "com.logonbox.vpn.RemoteUI";

	@DBusMemberName("Open")
    void open();

    @DBusMemberName("Ping")
    void ping();

    @DBusMemberName("Options")
    void options();

    @DBusMemberName("ConfirmExit")
    void confirmExit();

    @DBusMemberName("ExitApp")
    void exitApp();
    
	@Reflectable
	@TypeReflect(methods = true, constructors = true)
	public class ConfirmedExit extends DBusSignal {
		public ConfirmedExit(String path) throws DBusException {
			super(path);
		}
	}
}