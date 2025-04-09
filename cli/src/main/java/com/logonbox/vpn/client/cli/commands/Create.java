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
package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.common.ConnectionUtil;

import java.text.MessageFormat;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "create", aliases = { "cr" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Create a new VPN connection.")
public class Create extends AbstractConnectingCommand implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Option(names = { "-c",
			"--connect-at-startup" }, negatable = true, description = "Have this connection activate when the service starts.", defaultValue = "true")
	private boolean connectAtStartup = true;

	@Option(names = { "-s", "--stay-connected" }, negatable = true, description = "Stay connected.", defaultValue = "true")
	private boolean stayConnected = true;

	@Option(names = { "-n", "--dont-connect-now" }, description = "Just create the connection, don't connect yet.")
	private boolean dontConnectNow;

	@Option(names = { "-m", "--mode" }, description = "The mode of connection.")
	private Mode mode = Mode.CLIENT;

	@Parameters(index = "0", description = "The URI of the server to connect to. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path].")
	private String uri;

	@Override
	public Integer call() throws Exception {
		var uriObj = ConnectionUtil.getUri(uri);
		if (!uriObj.getScheme().equals("https")) {
			throw new IllegalArgumentException(CLI.BUNDLE.getString("error.onlyHttps"));
		}

		var cli = getCLI();
        
		var vpnManager = cli.getVpnManager();
        var vpn = vpnManager.getVpnOrFail();
        var connectionId = vpn.getConnectionIdForURI(uriObj.toASCIIString());
		var console = cli.getConsole();
		var err = console.err();
		var out = console.out();
		
		if (connectionId > 0) {
			throw new IllegalArgumentException(MessageFormat.format(CLI.BUNDLE.getString("error.alreadyExists"), uriObj));
		}

		var newConnectionId = vpn.createConnection(uriObj.toASCIIString(), connectAtStartup, stayConnected, mode.name());
		if (!dontConnectNow) {
		    return doConnect(cli, out, err, vpn.getConnection(newConnectionId), true);
		}

		return 0;
	}
}
