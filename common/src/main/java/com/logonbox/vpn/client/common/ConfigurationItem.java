/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.logonbox.vpn.client.common;


import com.sshtools.jaul.Phase;

import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public class ConfigurationItem<T> {
	
	public enum Scope {
		GLOBAL, USER
	}

    public final static ConfigurationItem<Level> LOG_LEVEL = add("logLevel", Level.class, Level.INFO, Level.TRACE, Level.DEBUG, Level.WARN, Level.INFO, Level.ERROR, Level.ERROR);
	public final static ConfigurationItem<Boolean> IGNORE_LOCAL_ROUTES = add("ignoreLocalRoutes", Boolean.class, true, true, false);
	public final static ConfigurationItem<String> DNS_INTEGRATION_METHOD = add("dnsIntegrationMethod", String.class, "AUTO");
	public final static ConfigurationItem<Boolean> AUTOMATIC_UPDATES = add("automaticUpdates", Boolean.class, Scope.USER, true, true, false);
    public final static ConfigurationItem<Boolean> IP_FORWARDING = add("ipForwarding", Boolean.class, Scope.GLOBAL, false, true, false);
	public final static ConfigurationItem<Boolean> SINGLE_ACTIVE_CONNECTION = add("singleActiveConnection", Boolean.class, Scope.GLOBAL, true, true, false);
	public final static ConfigurationItem<Phase> PHASE = add("phase", Phase.class, Phase.STABLE, Phase.values());
	public final static ConfigurationItem<Long> DEFER_UPDATE_UNTIL = add("deferUpdatesUntil", Long.class, Scope.USER, 0l);
	public final static ConfigurationItem<Integer> RECONNECT_DELAY = add("reconnectDelay", Integer.class, 5);
	public final static ConfigurationItem<Integer> MTU = add("mtu", Integer.class, Scope.GLOBAL, 0);
	public final static ConfigurationItem<Long> FAVOURITE = add("favourite", Long.class, Scope.USER, 0l);
	public final static ConfigurationItem<String> DEVICE_UUID = add("deviceUUID", String.class, Scope.USER, "");
	
	private final String key;
	private final Class<?> type;
	private final T defaultValue;
	private final Set<T> values;
	private final Scope scope;
	
	private static Map<String, ConfigurationItem<?>> items;

	
	private ConfigurationItem(String key, Class<T> type, T defaultValue, @SuppressWarnings("unchecked") T... values) {
		this(key, type, Scope.GLOBAL, defaultValue, values);	
	}
	
	private ConfigurationItem(String key, Class<T> type, Scope scope, T defaultValue, @SuppressWarnings("unchecked") T... values) {
		this.key = key;
		this.scope = scope;
		this.type = type;
		this.defaultValue = defaultValue;
		this.values = new LinkedHashSet<>(Arrays.asList(values));
	}
	
	static void add(ConfigurationItem<?> item) {
		if(items == null)
			items = new LinkedHashMap<>();
		items.put(item.getKey(), item);
	}
	
	public static <T> ConfigurationItem<T> add(String key, Class<T> type, T defaultValue, @SuppressWarnings("unchecked") T... values) {
		return add(key, type, Scope.GLOBAL, defaultValue, values);
	}
	
	public static <T> ConfigurationItem<T> add(String key, Class<T> type, Scope scope, T defaultValue, @SuppressWarnings("unchecked") T... values) {
		ConfigurationItem<T> item = new ConfigurationItem<>(key, type, scope, defaultValue, values);
		add(item);
		return item;
	}

	public static boolean has(String name) {
		return items.containsKey(name);
	}
	
	public static ConfigurationItem<?> get(String name) {
		ConfigurationItem<?> configurationItem = items.get(name);
		if(configurationItem == null)
			throw new IllegalArgumentException("No configuration item " + name);
		return configurationItem;
	}
	
	public static Collection<ConfigurationItem<?>> items() {
		return items.values();
	}
	
	public static Collection<String> keys() {
		return items.keySet();
	}

	public Scope getScope() {
		return scope;
	}

	public String getKey() {
		return key;
	}

	public Class<?> getType() {
		return type;
	}

	public T getDefaultValue() {
		return defaultValue;
	}

	public Set<T> getValues() {
		return values;
	}

	@Override
	public int hashCode() {
		return Objects.hash(key);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConfigurationItem<?> other = (ConfigurationItem<?>) obj;
		return Objects.equals(key, other.key);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public T parse(String val) {
		if(val == null)
			return defaultValue;
		try {
			if(type == Integer.class)
				return (T)((Integer)Integer.parseInt(val));
			else if(type == Long.class)
				return (T)((Long)Long.parseLong(val));
			else if(type == Boolean.class)
				return (T)((Boolean)Boolean.parseBoolean(val));
			else if(type == Level.class)
				return (T)Level.valueOf(val);
			else if(type == String.class)
				return (T)val;
			else if(Enum.class.isAssignableFrom(type)) {
		        return (T)Enum.valueOf((Class<Enum>)type, val);
			}
			else
				throw new IllegalArgumentException("Cannot parse type " + type);
		}
		catch(Exception e) {
			return defaultValue;
		}
	}
}
