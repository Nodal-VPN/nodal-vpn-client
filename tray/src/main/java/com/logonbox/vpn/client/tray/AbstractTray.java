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
package com.logonbox.vpn.client.tray;

import com.logonbox.vpn.client.common.TrayMode;
import com.logonbox.vpn.client.common.UiConfiguration;
import com.sshtools.jini.Data.Handle;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractTray implements AutoCloseable, Tray {

	protected static final int DEFAULT_ICON_SIZE = 64;

	protected TrayDaemon context;

	private boolean constructing;

    private final List<AutoCloseable> listeners;
    private final Handle handle;


	public AbstractTray(TrayDaemon context) throws Exception {
		constructing = true;
		this.context = context;
		
		var uiSection = UiConfiguration.get().ui();
		
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
                if(vu.key().equals(UiConfiguration.TRAY_MODE.getKey()) &&
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
