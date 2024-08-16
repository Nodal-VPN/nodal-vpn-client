package com.logonbox.vpn.client.service;

import com.logonbox.vpn.client.common.AppConstants;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.ConfigurationItem.Scope;
import com.logonbox.vpn.client.common.ConfigurationRepository;
import com.logonbox.vpn.client.common.Utils;
import com.sshtools.jini.Data;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.Monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class ConfigurationRepositoryImpl implements ConfigurationRepository {

	static Logger log = LoggerFactory.getLogger(ConfigurationRepositoryImpl.class);

//    private final INI doc;
    private final INISet set;

	public ConfigurationRepositoryImpl() {
	    var bldr = new INISet.Builder(AppConstants.CLIENT_SERVICE_NAME).
                withMonitor(new Monitor()).
                withApp(AppConstants.CLIENT_NAME).
                withoutSystemPropertyOverrides().
                withScopes(com.sshtools.jini.config.INISet.Scope.GLOBAL).
                withWriteScope(com.sshtools.jini.config.INISet.Scope.GLOBAL);

        if(AppVersion.isDeveloperWorkspace()) {
            bldr.withPath(com.sshtools.jini.config.INISet.Scope.GLOBAL, Paths.get("conf"));
        }
	    
        set = bldr.build();
        
        log.info("Configuration repository initialised.");
        log.info("  Global scope: {}", getDirectory());
	}

	@Override
    public Path getDirectory() {
        return set.appPathForScope(com.sshtools.jini.config.INISet.Scope.GLOBAL);
    }

	@Override
	public <V> V getValue(String owner, ConfigurationItem<V> key) {
		var node = getSection(owner, key);
		var strVal = node.get(key.getKey(), key.getDefaultValue().toString());
        V v = key.parse(strVal);
		if(key.getValues() != null && !key.getValues().isEmpty()) {
			if(!key.getValues().contains(v)) {
				log.warn("Invalid value found in {}. {} is not in {}, resetting to default {}", key.getKey(), v, key.getValues(), key.getDefaultValue());
                node.remove(key.getKey());
				return key.getDefaultValue();
			}
		}
		return v;
	}

	@Override
	public <V> void setValue(String owner, ConfigurationItem<V> key, V value) {
		var node = getSection(owner, key);
		var was = node.get(key.getKey(), key.getDefaultValue().toString());
		if (!Objects.equals(was, value.toString())) {
			log.info(String.format("Setting '%s' to '%s'", key.getKey(), value));
			node.put(key.getKey(), value.toString());
		}

	}

	protected <V> Data getSection(String owner, ConfigurationItem<V> key) {
		Data node;
		if(key.getScope() == Scope.USER && Utils.isBlank(owner)) {
			throw new IllegalArgumentException(String.format("No owner provided for a %s scoped configuration item, '%s'.", key.getScope(), key.getKey()));
		}
		else if(key.getScope() == Scope.USER) {
			node = set.document().obtainSection("users", owner);
		}
		else {
            node = set.document().obtainSection("global");
		}
		return node;
	}

}
