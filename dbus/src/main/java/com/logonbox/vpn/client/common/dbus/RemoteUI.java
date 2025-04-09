/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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