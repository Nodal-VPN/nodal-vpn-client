package com.logonbox.vpn.client.desktop.service.dbus;

import com.logonbox.vpn.client.desktop.service.DesktopServiceContext;

import org.freedesktop.dbus.connections.impl.DBusConnection;

public abstract class AbstractVPNComponent {

	protected static final String DIRECT = "DIRECT";
    protected final DesktopServiceContext ctx;

	protected AbstractVPNComponent(DesktopServiceContext ctx) {
		this.ctx = ctx;
	}

	void assertRegistered() {
		var sourceOrDefault = getSourceOrDefault();
        if (ctx.isRegistrationRequired() && !sourceOrDefault.equals(DIRECT) && !ctx.hasFrontEnd(sourceOrDefault)) {
			throw new IllegalStateException("Not registered.");
		}
	}
	
	public static String getSourceOrDefault() {
		var cinfo = DBusConnection.getCallInfo();
        return cinfo == null ? DIRECT : cinfo.getSource();
	}

	String getCurrentUser() {
		String src = getSourceOrDefault();
		if (ctx.hasFrontEnd(src)) {
			return ctx.getFrontEnd(src).getUsername();
		}
		return System.getProperty("user.name");
	}
}
