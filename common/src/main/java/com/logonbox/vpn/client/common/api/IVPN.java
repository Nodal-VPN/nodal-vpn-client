package com.logonbox.vpn.client.common.api;

import java.util.List;

public interface IVPN<CONX extends IVPNConnection> {

	List<CONX> getConnections();

	CONX getConnection(long id);
	
	long getMaxMemory();
	
	long getFreeMemory();

	String getUUID();

	String getVersion();

	String getDeviceName();

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
