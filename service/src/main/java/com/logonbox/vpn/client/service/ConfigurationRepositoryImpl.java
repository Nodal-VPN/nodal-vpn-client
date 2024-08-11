package com.logonbox.vpn.client.service;

import com.logonbox.vpn.client.common.AppConstants;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.ConfigurationItem.Scope;
import com.logonbox.vpn.client.common.ConfigurationRepository;
import com.logonbox.vpn.client.common.Utils;
import com.sshtools.jini.Data;
import com.sshtools.jini.INI;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.Monitor;
import com.sshtools.liftlib.OS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Objects;

public class ConfigurationRepositoryImpl implements ConfigurationRepository {

	static Logger log = LoggerFactory.getLogger(ConfigurationRepositoryImpl.class);


    private INI doc;

	public ConfigurationRepositoryImpl() {
	    var bldr = new INISet.Builder(AppConstants.CLIENT_SERVICE_NAME).
                withMonitor(new Monitor()).
                withApp(AppConstants.CLIENT_NAME).
                withoutSystemPropertyOverrides();

        if(AppVersion.isDeveloperWorkspace()) {
            bldr.withScopes(com.sshtools.jini.config.INISet.Scope.USER).
                withPath(com.sshtools.jini.config.INISet.Scope.USER, Paths.get("conf")).
                withWriteScope(com.sshtools.jini.config.INISet.Scope.USER);
        }
        else if(OS.isAdministrator()) {
            bldr.withScopes(com.sshtools.jini.config.INISet.Scope.GLOBAL).
                withWriteScope(com.sshtools.jini.config.INISet.Scope.GLOBAL);
	    }
	    else {
            bldr.withScopes(com.sshtools.jini.config.INISet.Scope.USER).
                withWriteScope(com.sshtools.jini.config.INISet.Scope.USER);
	    }
	    
        doc = bldr.build().document();
	}

	@Override
	public <V> V getValue(String owner, ConfigurationItem<V> key) {
		
		var node = getSection(owner, key);
		
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
		var node = getSection(owner, key);
		var was = node.get(key.getKey(), null);
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

	protected <V> Data getSection(String owner, ConfigurationItem<V> key) {
		Data node = doc;
		if(key.getScope() == Scope.USER && Utils.isBlank(owner)) {
			throw new IllegalArgumentException(String.format("No owner provided for a %s scoped configuration item, '%s'.", key.getScope(), key.getKey()));
		}
		else if(key.getScope() == Scope.USER) {
			node = node.obtainSection(owner);
		}
		return node;
	}

}
