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

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "connect", aliases = { "c", "co", "con" }, usageHelpAutoWidth = true,  mixinStandardHelpOptions = true, description = "Connect a VPN.")
public class Connect extends AbstractConnectingCommand implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
	private Optional<String> uri;

	@Override
	public Integer call() throws Exception {
		var cli = getCLI();
		var console = cli.getConsole();
		var out = console.out();
		var err = console.err();
		
        cli.initConsoleAndManager();

		IVpnConnection connection = null;

		if(uri.isPresent()) {
		    var uriVal = uri.get();
    		var pattern = getPattern(cli, new String[] { uriVal });
    		var c = getConnectionsMatching(pattern, cli);
    		if (c.isEmpty()) {
    			if (Utils.isNotBlank(uriVal)) {
    				if (!uriVal.startsWith("https://")) {
    					if (uriVal.indexOf("://") != -1) {
    						throw new IllegalArgumentException(CLI.BUNDLE.getString("error.onlyHttps"));
    					}
    					uriVal = "https://" + uriVal;
    				}
    				var uriObj = new URI(uriVal);
    				if (!uriObj.getScheme().equals("https")) {
    					throw new IllegalArgumentException(CLI.BUNDLE.getString("error.onlyHttps"));
    				}
    
    				var vpnManager = cli.getVpnManager();
                    var vpn = vpnManager.getVpnOrFail();
                    var connectionId = vpn.getConnectionIdForURI(uriVal);
    				connection = connectionId < 0 ? null : vpn.getConnection(connectionId);
    
    				if (connection == null) {
    					connectionId = vpn.connect(uriVal);
    					connection = vpn.getConnection(connectionId);
    					if (cli.isVerbose())
    						out.println(MessageFormat.format(CLI.BUNDLE.getString("info.created"), uriVal));
    				}
    			} else {
    				throw new IllegalStateException(CLI.BUNDLE.getString("error.missingUri"));
    			}
    		} else
    			connection = c.get(0);
		}
		else {
		    var allConnections = cli.getVpnManager().getVpnOrFail().getConnections();
            connection = Stream.of(allConnections).
                    filter(IVpnConnection::isFavourite).
                    findFirst().orElseGet(() -> Stream.of(allConnections).
                        findFirst().orElseThrow(() -> new IllegalStateException(CLI.BUNDLE.getString("error.noConnections"))));
		}
		
		console.flush();

		return doConnect(cli, out, err, connection, false);

	}
}
