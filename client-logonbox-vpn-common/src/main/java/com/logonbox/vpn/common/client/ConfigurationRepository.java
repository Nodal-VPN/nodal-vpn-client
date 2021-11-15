package com.logonbox.vpn.common.client;

public interface ConfigurationRepository {

	<V> V getValue(ConfigurationItem<V> item);
	
	<V> void setValue(ConfigurationItem<V> item, V value);
}
