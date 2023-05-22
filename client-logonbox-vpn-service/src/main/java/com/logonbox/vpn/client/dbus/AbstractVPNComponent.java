package com.logonbox.vpn.client.dbus;

import java.util.Objects;

import org.freedesktop.dbus.connections.impl.DBusConnection;

import com.logonbox.vpn.client.LocalContext;

public abstract class AbstractVPNComponent {

	private LocalContext ctx;

	protected AbstractVPNComponent(LocalContext ctx) {
		this.ctx = ctx;
	}

	void assertRegistered() {
		if (ctx.isRegistrationRequired() && !ctx.hasFrontEnd(getSourceOrDefault())) {
			throw new IllegalStateException("Not registered.");
		}
	}
	
	public static String getSourceOrDefault() {
		return Objects.requireNonNullElse(DBusConnection.getCallInfo().getSource(), "DIRECT");
	}

	String getOwner() {
		String src = getSourceOrDefault();
		if (ctx.hasFrontEnd(src)) {
			return ctx.getFrontEnd(src).getUsername();
		}
		return System.getProperty("user.name");
	}
}
