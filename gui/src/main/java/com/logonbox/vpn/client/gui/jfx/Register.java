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
package com.logonbox.vpn.client.gui.jfx;

import com.logonbox.vpn.client.common.AuthenticationCancelledException;
import com.logonbox.vpn.client.common.ServiceClient;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.io.IOException;
import java.net.URISyntaxException;

public final class Register<CONX extends IVpnConnection> implements Runnable {
	private final IVpnConnection selectedConnection;
	private final ServiceClient serviceClient;
	private final UI<CONX> ui;

	public Register(IVpnConnection selectedConnection, ServiceClient serviceClient, UI<CONX> ui) {
		this.selectedConnection = selectedConnection;
		this.serviceClient = serviceClient;
		this.ui = ui;
	}

	public void run() {
		new Thread() {
			public void run() {
				try {
					serviceClient.register(selectedConnection);
				} catch (AuthenticationCancelledException ae) {
					// Ignore, handled elsewhere
				} catch (IOException | URISyntaxException e) {
					UI.maybeRunLater(() -> ui.showError("Failed to register. " + e.getMessage(), e));
				}
			}
		}.start();
	}
}