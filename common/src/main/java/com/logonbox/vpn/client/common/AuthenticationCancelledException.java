package com.logonbox.vpn.client.common;

import java.io.IOException;

@SuppressWarnings("serial")
public class AuthenticationCancelledException extends IOException {
	
	public AuthenticationCancelledException() {
		super("Authentication cancelled.");
	}
}
