package com.logonbox.vpn.common.client;

import java.io.Closeable;
import java.net.URI;
import java.util.List;

public interface ConnectionRepository extends Closeable {

	Connection getConnection(Long id);

	Connection save(Connection connection);

	Connection createNew();

	Connection createNew(URI uriObj);

	Connection update(URI uriObj, Connection connection);

	List<Connection> getConnections(String owner);

	void delete(Connection con);

	Connection importConfiguration(String configuration);
}
