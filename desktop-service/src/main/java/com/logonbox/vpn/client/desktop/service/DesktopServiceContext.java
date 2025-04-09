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
package com.logonbox.vpn.client.desktop.service;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.dbus.VPNFrontEnd;
import com.logonbox.vpn.client.common.dbus.VpnConnection;

import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.messages.Message;

import java.util.Collection;

public interface DesktopServiceContext extends LocalContext<VpnConnection> {

    void deregisterFrontEnd(String source);

    boolean hasFrontEnd(String source);

    VPNFrontEnd registerFrontEnd(String source);

    void registered(VPNFrontEnd frontEnd);

    VPNFrontEnd getFrontEnd(String source);

    boolean isRegistrationRequired();

    Collection<VPNFrontEnd> getFrontEnds();

    void sendMessage(Message message);

    AbstractConnection getConnection();

}
