package com.logonbox.vpn.client.cli;

import com.logonbox.vpn.client.common.AppContext;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.sshtools.jaul.UpdateService;

import java.io.IOException;
import java.net.CookieStore;

public interface CLIContext extends AppContext<VpnConnection> {

	ConsoleProvider getConsole();

	PromptingCertManager getCertManager();

	void about() throws IOException;

	void exitWhenDone();

	boolean isQuiet();

	UpdateService getUpdateService();

	CookieStore getCookieStore();
}
