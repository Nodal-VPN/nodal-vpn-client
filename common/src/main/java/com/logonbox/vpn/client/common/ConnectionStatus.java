package com.logonbox.vpn.client.common;

import com.logonbox.vpn.drivers.lib.VpnInterfaceInformation;

public interface ConnectionStatus {

	public enum Type {
		DISCONNECTING,
		DISCONNECTED,
		TEMPORARILY_OFFLINE,
        BLOCKED,
		AUTHORIZING,
		CONNECTING,
		CONNECTED;

        public boolean isDisconnectable() {
            switch(this) {
            case TEMPORARILY_OFFLINE:
            case BLOCKED:
            case AUTHORIZING:
            case CONNECTED:
            case CONNECTING:
                return true;
            default:
                return false;
            }
        }	
	}
	
	VpnInterfaceInformation getDetail();
	
	Connection getConnection();
	
	Type getStatus();
	
	String getAuthorizeUri();
}
