package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.api.IVPN;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

@DBusInterfaceName("com.logonbox.vpn.VPN")
public interface VPN extends IVPN, DBusInterface {

    void ping();

    void deregister();

    void register(String username, boolean interactive, boolean supportsAuthorization);

//
	public class CertificatePrompt extends DBusSignal {

		private final String alertType;
		private final String title;
		private final String content;
		private final String key;
		private final String hostname;
		private final String message;

		public CertificatePrompt(String path, String alertType, String title, String content, String key,
				String hostname, String message) throws DBusException {
			super(path, alertType, title, content, key,
					hostname, message);
			this.alertType = alertType;
			this.title = title;
			this.content = content;
			this.hostname = hostname;
			this.message = message;
			this.key = key;
		}

		public String getAlertType() {
			return alertType;
		}

		public String getTitle() {
			return title;
		}

		public String getContent() {
			return content;
		}

		public String getKey() {
			return key;
		}

		public String getHostname() {
			return hostname;
		}

		public String getMessage() {
			return message;
		}
	}

	public class ConnectionAdded extends DBusSignal {

		private final Long id;

		public ConnectionAdded(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class ConnectionAdding extends DBusSignal {

		public ConnectionAdding(String path) throws DBusException {
			super(path);
		}
	}

	public class ConnectionRemoved extends DBusSignal {

		private final Long id;

		public ConnectionRemoved(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class ConnectionRemoving extends DBusSignal {

		private final Long id;

		public ConnectionRemoving(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class ConnectionUpdated extends DBusSignal {

		private final Long id;

		public ConnectionUpdated(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class ConnectionUpdating extends DBusSignal {

		private final Long id;

		public ConnectionUpdating(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class GlobalConfigChange extends DBusSignal {

		private final String name;
		private final String value;

		public GlobalConfigChange(String path, String name, String value) throws DBusException {
			super(path, name, value);
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

	}

	public static  class Exit extends DBusSignal {
		public Exit(String path) throws DBusException {
			super(path);
		}
	}

}
