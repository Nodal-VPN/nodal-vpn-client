package com.logonbox.vpn.client.tray;

import java.util.Arrays;
import java.util.List;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

public abstract class AbstractTray implements AutoCloseable, Tray, PreferenceChangeListener {

	protected static final int DEFAULT_ICON_SIZE = 48;

	protected TrayDaemon context;

	private boolean constructing;

    private List<AutoCloseable> listeners;

	public AbstractTray(TrayDaemon context) throws Exception {
		constructing = true;
		this.context = context;
		
		try {
            listeners = Arrays.asList(
                context.onVpnGone(this::reload),
                context.onVpnAvailable(() -> {
                    if(!constructing)
                        reload();
                }),
                context.onConnectionAdded(conx -> reload()),
                context.onConnectionUpdated(conx -> reload()),
                context.onConnectionRemoved(id -> reload()),
                context.onConnecting(conx -> reload()),
                context.onConnected(conx -> reload()),
                context.onDisconnecting((conx,reason) -> reload()),
                context.onDisconnected((conx, reason) -> reload()),
                context.onTemporarilyOffline((conx, reason) -> reload())
            );

            Configuration.getDefault().node().addPreferenceChangeListener(this);
        }
        finally {
            constructing = false;
        }
		
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent evt) {
		reload();
	}

	@Override
	public final void close() throws Exception {
	    listeners.forEach(l -> {
            try {
                l.close();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to close.", e);
            }
        });
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

}
