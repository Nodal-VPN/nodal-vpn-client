package com.logonbox.vpn.client.common;

import com.logonbox.vpn.drivers.lib.PlatformService;

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

    Connection getConnectionByPublicKey(String publicKey);

    void start(PlatformService<?> platformService);
}
