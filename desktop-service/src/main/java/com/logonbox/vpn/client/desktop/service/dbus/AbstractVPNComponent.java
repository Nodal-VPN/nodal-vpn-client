package com.logonbox.vpn.client.desktop.service.dbus;

import com.logonbox.vpn.client.desktop.service.DesktopServiceContext;

import org.freedesktop.dbus.connections.impl.DBusConnection;

import java.util.Objects;

public abstract class AbstractVPNComponent {

	protected final DesktopServiceContext ctx;

	protected AbstractVPNComponent(DesktopServiceContext ctx) {
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
