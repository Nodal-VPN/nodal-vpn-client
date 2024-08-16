package com.logonbox.vpn.client.common;

import java.nio.file.Path;

public interface ConfigurationRepository {

	<V> V getValue(String owner, ConfigurationItem<V> item);
	
	<V> void setValue(String owner, ConfigurationItem<V> item, V value);

    Path getDirectory();
}
