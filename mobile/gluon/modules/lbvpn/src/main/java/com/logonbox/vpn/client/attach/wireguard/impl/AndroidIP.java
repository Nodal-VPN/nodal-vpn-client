package com.logonbox.vpn.client.attach.wireguard.impl;

import com.logonbox.vpn.drivers.lib.AbstractVirtualInetAddress;

import java.io.IOException;

/**
 * Represents an active or inactive Wireguard tunnel on tunnel.
 */
public class AndroidIP extends AbstractVirtualInetAddress<AndroidPlatformService> {

	/**
	 * @param platform platform
	 */
	public AndroidIP(String name, String nativeName, AndroidPlatformService platform) {
		super(name, nativeName, platform);
	}

    @Override
    public void delete() throws IOException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String displayName() {
        // TODO Auto-generated method stub
        return null;
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
    public boolean isUp() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void up() throws IOException {
        // TODO Auto-generated method stub
        
    }

	

}
