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
package com.logonbox.vpn.client.common;

import com.jadaptive.nodal.core.lib.VpnInterfaceInformation;

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

        public boolean isPrompt() {
            return this == AUTHORIZING;
        }

        public boolean isSuccess() {
            switch(this) {
            case CONNECTED:
            case CONNECTING:
                return true;
            default:
                return false;
            }
        }

        public boolean isWarn() {
            switch(this) {
            case BLOCKED:
            case DISCONNECTING:
            case TEMPORARILY_OFFLINE:
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
