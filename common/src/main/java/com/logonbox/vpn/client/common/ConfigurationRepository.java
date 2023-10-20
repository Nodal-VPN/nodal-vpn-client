package com.logonbox.vpn.client.common;

public interface ConfigurationRepository {

	<V> V getValue(String owner, ConfigurationItem<V> item);
	
	<V> void setValue(String owner, ConfigurationItem<V> item, V value);
}
