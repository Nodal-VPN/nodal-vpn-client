package com.logonbox.vpn.client.cli;

import java.io.IOException;
import java.net.CookieStore;
import java.util.List;

import org.freedesktop.dbus.connections.AbstractConnection;

import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.UpdateService;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

public interface CLIContext {
	VPN getVPN();

	VPNConnection getVPNConnection(long connectionId);

	ConsoleProvider getConsole();

	List<VPNConnection> getVPNConnections();

	PromptingCertManager getCertManager();

	void about() throws IOException;

	void exitWhenDone();

	boolean isQuiet();

	AbstractConnection getBus();

	UpdateService getUpdateService();

	CookieStore getCookieStore();
}
