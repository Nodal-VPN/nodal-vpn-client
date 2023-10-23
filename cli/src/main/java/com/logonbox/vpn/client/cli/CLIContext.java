package com.logonbox.vpn.client.cli;

import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.UpdateService;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.dbus.VPNConnection;

import org.freedesktop.dbus.connections.AbstractConnection;

import java.io.IOException;
import java.net.CookieStore;

public interface CLIContext {

    VpnManager<VPNConnection> getVpnManager();
    
	ConsoleProvider getConsole();

	PromptingCertManager getCertManager();

	void about() throws IOException;

	void exitWhenDone();

	boolean isQuiet();

	AbstractConnection getBus();

	UpdateService getUpdateService();

	CookieStore getCookieStore();
}
