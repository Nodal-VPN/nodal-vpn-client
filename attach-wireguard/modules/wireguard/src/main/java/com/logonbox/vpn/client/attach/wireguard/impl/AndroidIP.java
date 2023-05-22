package com.logonbox.vpn.client.attach.wireguard.impl;

import java.io.IOException;

import com.logonbox.vpn.client.wireguard.AbstractVirtualInetAddress;

/**
 * Represents an active or inactive Wireguard tunnel on tunnel.
 */
public class AndroidIP extends AbstractVirtualInetAddress<AndroidPlatformService> {

	/**
	 * @param platform platform
	 */
	public AndroidIP(AndroidPlatformService platform) {
		super(platform);
	}

	@Override
	public boolean isUp() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void delete() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void down() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getMac() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDisplayName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void up() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dns(String[] dns) throws IOException {
		// TODO Auto-generated method stub
		
	}

}
