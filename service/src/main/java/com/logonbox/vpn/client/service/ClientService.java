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
package com.logonbox.vpn.client.service;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.Connection.Mode;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface ClientService<CONX extends IVpnConnection>  {
	
	public interface Listener {
		void configurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue);
	}
	
	int CONNECT_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.connectTimeout", "12"));
	int HANDSHAKE_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.handshakeTimeout", "180"));
	int SERVICE_WAIT_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.serviceWaitTimeout", "2"));

	void addListener(Listener listener);
	
	void removeListener(Listener listener);
	
	String getDeviceName() ;
	
	UUID getUUID(String owner) ;
	
	Connection save(Connection c) ;
	
	void connect(Connection c) ;
	
	Connection connect(String owner, String uri) ;
	
	void disconnect(Connection c, String reason) ;

	List<ConnectionStatus> getStatus(String owner) ;

	void ping() ;

	ConnectionStatus.Type getStatusType(Connection con) ;

	ConnectionStatus getStatus(String owner, String uri) ;

	ConnectionStatus getStatus(long id) ;

	void requestAuthorize(Connection connection) ;

	void authorized(Connection connection) ;

	void deauthorize(Connection connection) ;

	boolean hasStatus(String owner, String uri) ;

	ConnectionStatus getStatusForPublicKey(String publicKey) ;

	void delete(Connection connection) ;

	<V> V getValue(String owner, ConfigurationItem<V> item) ;
	
	<V> void setValue(String owner, ConfigurationItem<V> item, V value) ;  

	String getActiveInterface(Connection c);
	
	IOException getConnectionError(Connection connection);

	void stopService();

	String[] getKeys();

	List<Connection> getConnections(String owner);

	void restart();

	Connection create(String uri, String owner, boolean connectAtStartup, Mode mode, boolean stayConnected);

	Connection importConfiguration(String owner, String configuration);

	boolean isMatchesAnyServerURI(String owner, String uri);

	Connection getConnectionStatus(Connection connection) throws IOException;

	LocalContext<CONX> getContext();
}
