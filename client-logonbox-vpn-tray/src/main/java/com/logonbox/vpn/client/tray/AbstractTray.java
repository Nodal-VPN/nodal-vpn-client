package com.logonbox.vpn.client.tray;

import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;

import com.logonbox.vpn.common.client.AbstractDBusClient.BusLifecycleListener;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

public abstract class AbstractTray implements AutoCloseable, Tray, BusLifecycleListener, PreferenceChangeListener {

	protected static final int DEFAULT_ICON_SIZE = 48;

	protected TrayDaemon context;

	private boolean constructing;

	public AbstractTray(TrayDaemon context) throws Exception {
		constructing = true;
		this.context = context;
		try {
			context.addBusLifecycleListener(this);
			Configuration.getDefault().node().addPreferenceChangeListener(this);
		} finally {
			constructing = false;
		}
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent evt) {
		reload();
	}

	@Override
	public final void close() throws Exception {
		context.removeBusLifecycleListener(this);
		Configuration.getDefault().node().removePreferenceChangeListener(this);
		onClose();
	}

	@Override
	public void setProgress(int progress) {
	}

	@Override
	public void setAttention(boolean enabled, boolean critical) {
	}

	protected abstract void onClose() throws Exception;

	protected boolean isDark() {
		String mode = Configuration.getDefault().darkMode();
		if (mode.equals(Configuration.DARK_MODE_AUTO))
			return isDefaultDark();
		else if (mode.equals(Configuration.DARK_MODE_ALWAYS))
			return true;
		else
			return false;
	}
	
	protected abstract boolean isDefaultDark();

	@Override
	public void busInitializer(DBusConnection connection) {

		try {
			connection.addSigHandler(VPN.ConnectionAdded.class, new DBusSigHandler<VPN.ConnectionAdded>() {
				@Override
				public void handle(VPN.ConnectionAdded sig) {
					reload();
				}
			});
			connection.addSigHandler(VPN.ConnectionRemoved.class, new DBusSigHandler<VPN.ConnectionRemoved>() {
				@Override
				public void handle(VPN.ConnectionRemoved sig) {
					reload();
				}
			});
			connection.addSigHandler(VPN.ConnectionUpdated.class, new DBusSigHandler<VPN.ConnectionUpdated>() {
				@Override
				public void handle(VPN.ConnectionUpdated sig) {
					reload();
				}
			});

			connection.addSigHandler(VPNConnection.Connecting.class, new DBusSigHandler<VPNConnection.Connecting>() {
				@Override
				public void handle(VPNConnection.Connecting sig) {
					reload();
				}
			});
			connection.addSigHandler(VPNConnection.Connected.class, new DBusSigHandler<VPNConnection.Connected>() {
				@Override
				public void handle(VPNConnection.Connected sig) {
					reload();
				}
			});
			connection.addSigHandler(VPNConnection.Disconnected.class,
					new DBusSigHandler<VPNConnection.Disconnected>() {
						@Override
						public void handle(VPNConnection.Disconnected sig) {
							reload();
						}
					});
			connection.addSigHandler(VPNConnection.TemporarilyOffline.class,
					new DBusSigHandler<VPNConnection.TemporarilyOffline>() {
						@Override
						public void handle(VPNConnection.TemporarilyOffline sig) {
							reload();
						}
					});
			connection.addSigHandler(VPNConnection.Disconnecting.class,
					new DBusSigHandler<VPNConnection.Disconnecting>() {
						@Override
						public void handle(VPNConnection.Disconnecting sig) {
							reload();
						}
					});
			if (!constructing) {
				reload();
			}
		} catch (DBusException dbe) {
			throw new IllegalStateException("Failed to configure.", dbe);
		}

	}

	@Override
	public void busGone() {
		reload();
	}

}
