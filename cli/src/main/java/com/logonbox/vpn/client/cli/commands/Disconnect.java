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
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.text.MessageFormat;
import java.util.ArrayList;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "disconnect", aliases = { "dis", "di", "d", "dc", "close" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Disconnect from a VPN.")
public class Disconnect extends AbstractConnectionCommand {

	@Parameters(description = "Connection names, URLs or IDs to disconnect. If none supplied, all connections will be disconnected.")
	private String[] names;

	@Option(names = { "-d", "--delete" }, description = "Permanently delete the connection as well.")
	private boolean delete;

	@Override
	public Integer call() throws Exception {

		var cli = getCLI();
        var console = cli.getConsole();
        var l = new ArrayList<IVpnConnection>();
        
		if ((names == null || names.length == 0)) {
		    for(var c : cli.getVpnManager().getVpnOrFail().getConnections()) {
                var statusType = Type.valueOf(c.getStatus());
                if (statusType.isDisconnectable()) {
                    l.add(c);
                }
            }
		}
		else {    
			var pattern = getPattern(cli, names);
			for (var c : getConnectionsMatching(pattern, cli)) {
				var statusType = Type.valueOf(c.getStatus());
                if (statusType.isDisconnectable()) {
                    l.add(c);
				} 
			}
		}
		
		if(l.isEmpty()) {
		    throw new IllegalStateException(CLI.BUNDLE.getString("error.noActiveConnections"));
		}
		
		for(var c : l) {
    		disconnect(c, cli);
            if (delete) {
                c.delete();
                if (cli.isVerbose()) {
                    console.out().println(MessageFormat.format(CLI.BUNDLE.getString("info.deleted"), c.getUri(true)));
                    console.flush();
                }
            }
		}
		
		return 0;
	}

}
