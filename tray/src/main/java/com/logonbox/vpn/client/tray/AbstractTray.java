package com.logonbox.vpn.client.tray;

import com.logonbox.vpn.client.common.AppConstants;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.ConfigurationItem.TrayMode;
import com.sshtools.jini.Data.Handle;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractTray implements AutoCloseable, Tray {

	protected static final int DEFAULT_ICON_SIZE = 48;

	protected TrayDaemon context;

	private boolean constructing;

    private final List<AutoCloseable> listeners;
    private final Handle handle;

    protected final Section uiSection;


	public AbstractTray(TrayDaemon context) throws Exception {
		constructing = true;
		this.context = context;
		
		var iniSet = new INISet.Builder(AppConstants.CLIENT_NAME).
                withMonitor(new Monitor()).
                withApp(AppConstants.CLIENT_NAME).
                withoutSystemPropertyOverrides().
                withWriteScope(Scope.USER).
                build();
		
		uiSection = iniSet.document().obtainSection("ui");
		
		try {
            var mgr = context.getVpnManager();
            listeners = Arrays.asList(
                mgr.onVpnGone(this::reload),
                mgr.onVpnAvailable(() -> {
                    if(!constructing)
                        reload();
                }),
                mgr.onConnectionAdded(conx -> reload()),
                mgr.onConnectionUpdated(conx -> reload()),
                mgr.onConnectionRemoved(id -> reload()),
                mgr.onConnecting(conx -> reload()),
                mgr.onConnected(conx -> reload()),
                mgr.onDisconnecting((conx,reason) -> reload()),
                mgr.onDisconnected((conx, reason) -> reload()),
                mgr.onTemporarilyOffline((conx, reason) -> reload())
            );

            handle = uiSection.onValueUpdate(vu -> {
                if(vu.key().equals(ConfigurationItem.TRAY_MODE.getKey()) &&
                   Arrays.asList(vu.newValues()).contains(TrayMode.OFF.name())
                ) {
                    try {
                        close();
                    } catch (Exception e) {
                    } finally {
                        System.exit(0);
                    }
                }
                else {
                    reload();
                }
            });
        }
        finally {
            constructing = false;
        }
		
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
	    handle.close();
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
