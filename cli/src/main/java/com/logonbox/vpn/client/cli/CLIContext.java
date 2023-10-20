package com.logonbox.vpn.client.cli;

import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.UpdateService;
import com.logonbox.vpn.client.common.api.IVPN;
import com.logonbox.vpn.client.common.api.IVPNConnection;

import org.freedesktop.dbus.connections.AbstractConnection;

import java.io.IOException;
import java.net.CookieStore;
import java.util.List;
import java.util.Optional;

public interface CLIContext {
	Optional<IVPN> getVPN();
	
    default IVPN getVPNOrFail() {
        return getVPN().orElseThrow(() -> new IllegalStateException("VPN service not available."));
    }

	IVPNConnection getVPNConnection(long connectionId);

	ConsoleProvider getConsole();

	List<IVPNConnection> getVPNConnections();

	PromptingCertManager getCertManager();

	void about() throws IOException;

	void exitWhenDone();

	boolean isQuiet();

	AbstractConnection getBus();

	UpdateService getUpdateService();

	CookieStore getCookieStore();
}
