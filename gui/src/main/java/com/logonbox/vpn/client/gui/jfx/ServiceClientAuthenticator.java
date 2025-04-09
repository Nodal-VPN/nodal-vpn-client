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
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.ServiceClient.DeviceCode;
import com.logonbox.vpn.client.common.ServiceClient.NameValuePair;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.lbapi.InputField;
import com.logonbox.vpn.client.common.lbapi.LogonResult;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Semaphore;

import javax.net.ssl.HostnameVerifier;

import jakarta.json.JsonObject;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

public final class ServiceClientAuthenticator<CONX extends IVpnConnection> implements ServiceClient.Authenticator {
	private final WebEngine engine;
	private final Semaphore authorizedLock;
	private final UIContext<CONX> context;
	private final VpnManager<CONX> vpnManager;
	private Map<InputField, NameValuePair> results;
	private LogonResult result;
	private boolean error;

	public ServiceClientAuthenticator(UIContext<CONX> context, WebEngine engine, Semaphore authorizedLock) {
		this.engine = engine;
		this.context = context;
		this.authorizedLock = authorizedLock;
		
		vpnManager = context.getAppContext().getVpnManager();
	}

	public void cancel() {
		UI.LOG.info("Cancelling authorization.");
		error = true;
		authorizedLock.release();
	}

	public void submit(JSObject obj) {
		for (var field : result.formTemplate().inputFields()) {
			results.put(field, new NameValuePair(field.resourceKey(),
					UI.memberOrDefault(obj, field.resourceKey(), String.class, "")));
		}
		authorizedLock.release();
	}

	@Override
	public String getUUID() {
		return vpnManager.getVpnOrFail().getUUID();
	}

	@Override
	public HostnameVerifier getHostnameVerifier() {
		return context.getAppContext().getCertManager();
	}

	@Override
	public void error(JsonObject i18n, LogonResult logonResult) {
		UI.maybeRunLater(() -> {
			if (logonResult.lastErrorIsResourceKey())
				engine.executeScript("authorizationError('"
						+ Utils.escapeEcmaScript(i18n.getString(logonResult.errorMsg())) + "');");
			else
				engine.executeScript("authorizationError('"
						+ Utils.escapeEcmaScript(logonResult.errorMsg()) + "');");

		});
	}
	
    @Override
    public void prompt(DeviceCode code) throws AuthenticationCancelledException {
        /* V3 VPN Server OAuth based authentication */
        UI.maybeRunLater(() -> {
            JSObject jsobj = (JSObject) engine.executeScript("window");
            jsobj.setMember("code", code);
            engine.executeScript("promptForCode();");
        });
    }

	@Override
	public void collect(JsonObject i18n, LogonResult result, Map<InputField, NameValuePair> results)
			throws IOException {
		this.results = results;
		this.result = result;

		UI.maybeRunLater(() -> {
			JSObject jsobj = (JSObject) engine.executeScript("window");
			jsobj.setMember("remoteBundle", i18n);
			jsobj.setMember("result", result);
			jsobj.setMember("results", results);
			engine.executeScript("collect();");
		});
		try {
			UI.LOG.info("Waiting for authorize semaphore to be released.");
			authorizedLock.acquire();
		} catch (InterruptedException e) {
			// TODO:
            error = true;
		}

		UI.LOG.info("Left authorization.");
		if (error)
			throw new AuthenticationCancelledException();
	}

	@Override
	public void authorized() throws IOException {
		UI.LOG.info("Authorized.");
		UI.maybeRunLater(() -> {
			engine.executeScript("authorized();");
			authorizedLock.release();
		});
	}
}