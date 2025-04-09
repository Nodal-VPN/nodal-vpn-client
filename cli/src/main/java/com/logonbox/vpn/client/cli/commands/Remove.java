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

import java.text.MessageFormat;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "remove", aliases = { "rm", "rem", "r", "delete", "del" }, usageHelpAutoWidth = true,  mixinStandardHelpOptions = true, description = "Delete connections.")
public class Remove extends AbstractConnectionCommand {

	@Parameters(description = "Connection names to delete.")
	private String[] names;

	@Override
	public Integer call() throws Exception {
	    
		var cli = getCLI();
        
		var pattern = getPattern(cli, names);
		var c = getConnectionsMatching(pattern, cli);
		var console = cli.getConsole();
		
		if (c.isEmpty()) {
            throw new IllegalArgumentException(MessageFormat.format(CLI.BUNDLE.getString("error.noMatch"), pattern));
		}
		for (var connection : c) {
			var status = Type.valueOf(connection.getStatus());
			if (status != Type.DISCONNECTED) {
				connection.disconnect("");
			}
			var uri = connection.getUri(true);
			connection.delete();
			if (cli.isVerbose()) {
                console.out().println(MessageFormat.format(CLI.BUNDLE.getString("info.deleted"), uri));
				console.flush();
			}
		}

		return 0;

	}
}
