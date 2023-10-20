package com.logonbox.vpn.client.service;

import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.drivers.lib.VpnInterfaceInformation;

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
