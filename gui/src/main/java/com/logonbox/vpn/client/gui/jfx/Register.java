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