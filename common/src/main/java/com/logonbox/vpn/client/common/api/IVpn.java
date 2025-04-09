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
package com.logonbox.vpn.client.common.api;

public interface IVpn<CONX extends IVpnConnection> {

	CONX[] getConnections();

    CONX[] getAllConnections();

	CONX getConnection(long id);
	
	long getMaxMemory();
	
	long getFreeMemory();

	String getUUID();

	String getVersion();

	String getDeviceName();

    String[] getAvailableDNSMethods();

	void shutdown(boolean restart);
	
	long importConfiguration(String configuration);

	long getConnectionIdForURI(String uri);

	long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode);

	int getNumberOfConnections();

	long connect(String uri);

	String getValue(String key);

	int getIntValue(String key);

	long getLongValue(String key);

	boolean getBooleanValue(String key);
	
	void setValue(String key, String value); 
	
	void setIntValue(String key, int value); 
	
	void setLongValue(String key, long value); 
	
	void setBooleanValue(String key, boolean value);
	
	void disconnectAll();

	int getActiveButNonPersistentConnections();
	
	boolean isCertAccepted(String encodedKey);
	
	void acceptCert(String encodedKey);
	
	void rejectCert(String encodedKey);
	
	void saveCert(String encodedKey);

	String[] getKeys();

	boolean isMatchesAnyServerURI(String uri);

}
