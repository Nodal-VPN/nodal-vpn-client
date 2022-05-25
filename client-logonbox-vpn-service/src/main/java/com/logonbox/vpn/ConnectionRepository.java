package com.logonbox.vpn;

import java.io.Closeable;
import java.net.URI;
import java.util.List;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.Connection;

public interface ConnectionRepository extends Closeable {
	
	void init(LocalContext context);

	default Connection getConnection(Long id) {
		for(Connection c : getConnections(null)) {
			if(c.getId().equals(id)) {
				return c;
			}
		}
		return null;
	}
	
	default void prepare(Connection connection) {
	}

	Connection save(Connection connection);

	Connection createNew();

	Connection createNew(URI uriObj);

	Connection update(URI uriObj, Connection connection);

	List<Connection> getConnections(String owner);

	void delete(Connection con);

	Connection importConfiguration(String configuration);
	
	default boolean isReadOnly() {
		return false;
	}
	
	default boolean isSingleConnection() {
		return false;
	}
}
