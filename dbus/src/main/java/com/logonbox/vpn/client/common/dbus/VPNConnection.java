package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.api.IVPNConnection;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

@DBusInterfaceName("com.logonbox.vpn.Connection")
public interface VPNConnection extends DBusInterface, IVPNConnection {

	public static class Connected extends DBusSignal {
		public Connected(String path) throws DBusException {
			super(path);
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public static class Connecting extends DBusSignal {
		public Connecting(String path) throws DBusException {
			super(path);
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public class Failed extends DBusSignal {

		private final String reason;
		private final String cause;
		private final String trace;

		public Failed(String path, String reason, String cause, String trace) throws DBusException {
			super(path, reason, cause, trace);
			this.cause = cause;
			this.trace = trace;
			this.reason = reason;
		}

		public String getCause() {
			return cause;
		}

		public String getTrace() {
			return trace;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public static class TemporarilyOffline extends DBusSignal {

		private final String reason;

		public TemporarilyOffline(String path, String reason) throws DBusException {
			super(path, reason);
			this.reason = reason;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public static class Disconnected extends DBusSignal {

		private final String reason;
		private final String displayName;
		private final String hostname;

		public Disconnected(String path, String reason, String displayName, String hostname) throws DBusException {
			super(path, reason, displayName, hostname);
			this.reason = reason;
			this.displayName = displayName;
			this.hostname = hostname;
		}

		public String getDisplayName() {
			return displayName;
		}

		public String getHostname() {
			return hostname;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public static class Disconnecting extends DBusSignal {

		private final String reason;

		public Disconnecting(String path, String reason) throws DBusException {
			super(path, reason);
			this.reason = reason;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public static class Authorize extends DBusSignal {

		private final String uri;
		private final String mode;
		
		public Authorize(String path, String uri, String mode) throws DBusException {
			super(path, uri, mode);
			this.uri = uri;
			this.mode = mode;
		}

		public String getMode() {
			return mode;
		}

		public String getUri() {
			return uri;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}



}
