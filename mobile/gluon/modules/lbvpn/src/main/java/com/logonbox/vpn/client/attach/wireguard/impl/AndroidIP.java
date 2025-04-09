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
