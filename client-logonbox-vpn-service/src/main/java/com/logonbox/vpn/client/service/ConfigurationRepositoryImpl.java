package com.logonbox.vpn.client.service;

import java.util.Objects;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.ConfigurationItem;
import com.logonbox.vpn.common.client.ConfigurationRepository;

public class ConfigurationRepositoryImpl implements ConfigurationRepository {

	static Logger log = LoggerFactory.getLogger(ConfigurationRepositoryImpl.class);

	private static Preferences NODE = Preferences.userNodeForPackage(ConfigurationRepository.class);

	public ConfigurationRepositoryImpl() {
	}

	@Override
	public <V> V getValue(ConfigurationItem<V> key) {
		V v = key.parse(NODE.get(key.getKey(), null));
		if(key.getValues() != null) {
			if(!key.getValues().contains(v)) {
				log.warn(String.format("Invalid value found in %s, resetting to default", key.getKey(), key.getDefaultValue()));
				setValue(key, key.getDefaultValue());
				return key.getDefaultValue();
			}
		}
		return v;
	}

	@Override
	public <V> void setValue(ConfigurationItem<V> key, V value) {
		String was = NODE.get(key.getKey(), null);
		if (!Objects.equals(was, value)) {
			if (value == null) {
				log.info(String.format("Setting '%s' to default value", key));
				NODE.remove(key.getKey());
			} else {
				log.info(String.format("Setting '%s' to '%s'", key, value));
				NODE.put(key.getKey(), value.toString());
			}
		}

	}

}
