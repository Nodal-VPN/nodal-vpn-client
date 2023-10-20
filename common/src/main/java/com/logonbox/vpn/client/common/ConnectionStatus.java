package com.logonbox.vpn.client.common;

import com.logonbox.vpn.drivers.lib.VpnInterfaceInformation;

public interface ConnectionStatus {

	public enum Type {
		DISCONNECTING,
		DISCONNECTED,
		TEMPORARILY_OFFLINE,
		AUTHORIZING,
		CONNECTING,
		CONNECTED;	
	}
	
	VpnInterfaceInformation getDetail();
	
	Connection getConnection();
	
	Type getStatus();
	
	String getAuthorizeUri();
}
