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
package com.logonbox.vpn.client.service;

import com.jadaptive.nodal.core.lib.VpnInterfaceInformation;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionStatus;

public class ConnectionStatusImpl implements ConnectionStatus {

	private Type status;
	private Connection connection;
	private VpnInterfaceInformation detail;
	private String authorizeUri;

	public ConnectionStatusImpl(Connection connection, VpnInterfaceInformation detail, Type status, String authorizeUri) {
		this.connection = connection;
		this.status = status;
		this.detail = detail;
		this.authorizeUri = authorizeUri;
	}

	@Override
	public String getAuthorizeUri() {
		return authorizeUri;
	}

	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public Type getStatus() {
		return status;
	}

	@Override
	public VpnInterfaceInformation getDetail() {
		return detail;
	}
}
