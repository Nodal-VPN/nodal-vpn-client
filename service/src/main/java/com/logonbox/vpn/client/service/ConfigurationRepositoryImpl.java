package com.logonbox.vpn.client.service;

import java.util.Objects;
import java.util.prefs.Preferences;

import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.ConfigurationRepository;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.ConfigurationItem.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationRepositoryImpl implements ConfigurationRepository {

	static Logger log = LoggerFactory.getLogger(ConfigurationRepositoryImpl.class);

	private static Preferences NODE = Preferences.userNodeForPackage(ConfigurationRepository.class);

	public ConfigurationRepositoryImpl() {
	}

	@Override
	public <V> V getValue(String owner, ConfigurationItem<V> key) {
		
		Preferences node = getNode(owner, key);
		
		if(Utils.isNotBlank(owner)) {
			/* For backwards compatibility before scoped configuration was introduced */
			var oldVal = NODE.get(key.getKey() + "." + owner, ""); 
			if(!oldVal.equals("")) {
				NODE.remove(key.getKey() + "." + owner);
				node.put(key.getKey(), oldVal);
			}
		}
		V v = key.parse(node.get(key.getKey(), null));
		if(key.getValues() != null && !key.getValues().isEmpty()) {
			if(!key.getValues().contains(v)) {
				log.warn(String.format("Invalid value found in %s. %s is not in %s, resetting to default", key.getKey(), v, key.getValues(), key.getDefaultValue()));
				setValue(owner, key, key.getDefaultValue());
				return key.getDefaultValue();
			}
		}
		return v;
	}

	@Override
	public <V> void setValue(String owner, ConfigurationItem<V> key, V value) {
		Preferences node = getNode(owner, key);
		
		if(Utils.isNotBlank(owner)) {
			/* For backwards compatibility before scoped configuration was introduced.
			 * It is unlikely the key will be set before it is get, but just in case */
			var oldVal = NODE.get(key.getKey() + "." + owner, ""); 
			if(!oldVal.equals("")) {
				NODE.remove(key.getKey() + "." + owner);
			}
		}
		
		String was = node.get(key.getKey(), null);
		if (!Objects.equals(was, value)) {
			if (value == null) {
				log.info(String.format("Setting '%s' to default value", key.getKey()));
				node.remove(key.getKey());
			} else {
				log.info(String.format("Setting '%s' to '%s'", key.getKey(), value));
				node.put(key.getKey(), value.toString());
			}
		}

	}

	protected <V> Preferences getNode(String owner, ConfigurationItem<V> key) {
		Preferences node = NODE;
		if(key.getScope() == Scope.USER && Utils.isBlank(owner)) {
			throw new IllegalArgumentException(String.format("No owner provided for a %s scoped configuration item, '%s'.", key.getScope(), key.getKey()));
		}
		else if(key.getScope() == Scope.USER) {
			node = node.node(owner);
		}
		return node;
	}

}
