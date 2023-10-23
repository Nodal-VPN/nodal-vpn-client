package com.logonbox.vpn.client.common.lbapi;

public enum PrincipalType {

	USER,
	GROUP,
	SERVICE,
	SYSTEM;
	
	public static final PrincipalType[] ALL_TYPES = { USER, GROUP, SERVICE, SYSTEM };
	
}
