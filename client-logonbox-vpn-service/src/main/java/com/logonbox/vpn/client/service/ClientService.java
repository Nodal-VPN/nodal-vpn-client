package com.logonbox.vpn.client.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import com.logonbox.vpn.common.client.ConfigurationItem;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public interface ClientService  {
	
	public interface Listener {
		void configurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue);
	}
	
	int CONNECT_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.connectTimeout", "12"));
	int HANDSHAKE_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.handshakeTimeout", "180"));
	int SERVICE_WAIT_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.serviceWaitTimeout", "2"));

	default String[] getMissingPackages() {
		return new String[0];
	}
	
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

	ScheduledExecutorService getTimer();

	void registered(VPNFrontEnd frontEnd);

	String getActiveInterface(Connection c);
	
	IOException getConnectionError(Connection connection);

	void stopService();

	String[] getKeys();

	List<Connection> getConnections(String owner);

	void restart();

	Connection create(String uri, String owner, boolean connectAtStartup, Mode mode, boolean stayConnected);

	Connection importConfiguration(String owner, String configuration);

	boolean isMatchesAnyServerURI(String owner, String uri);
}
