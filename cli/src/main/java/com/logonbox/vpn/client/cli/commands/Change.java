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

import java.text.MessageFormat;
import java.util.Optional;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "change", aliases = {"ch", "ed", "edit" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Edit connections.")
public class Change extends AbstractConnectionCommand {

	@Parameters(description = "Connection name, URL or ID to edit.")
	private String name;

	@Option(names = { "-n", "--name" }, description = "Set the name.")
	private Optional<String> newName;

	@Option(names = { "-u", "--uri" }, description = "Set the URI.")
	private Optional<String> uri;

	@Option(names = { "-s",
			"--connect-at-startup" }, negatable = true, description = "Set the connect at startup option.")
	private Optional<Boolean> connectAtStartup;

    @Option(names = { "-f",
            "--favourite" }, negatable = true, description = "Set the favourite option.")
    private Optional<Boolean> favourite;

	@Option(names = { "-c",
			"--stay-connected" }, negatable = true, description = "Set the stay connected option.")
	private Optional<Boolean> stayConnected;

    @Option(names = { "-S",
            "--shared" }, negatable = true, description = "Set whether the connection is shared to all users (only administrator).")
    private Optional<Boolean> shared;

	@Override
	public Integer call() throws Exception {
		var cli = getCLI();
        
		var pattern = getPattern(cli, name);

		var c = getConnectionsMatching(pattern, cli);
		if (c.isEmpty()) {
		    throw new IllegalArgumentException(MessageFormat.format(CLI.BUNDLE.getString("error.noMatch"), pattern));
		} else if (c.size() == 1) {
			var connection = c.get(0);
			connection.update(newName.orElseGet(() -> connection.getName()),
			        uri.orElseGet(() -> connection.getUri(false)),
			        connectAtStartup.orElseGet(() -> connection.isConnectAtStartup()),
			        stayConnected.orElseGet(() -> connection.isStayConnected()));
			if(favourite.isPresent()) {
			    connection.setAsFavourite();
			}
			if(shared.isPresent()) {
			    connection.setShared(shared.get());
			    connection.save();
			}
			if (cli.isVerbose())
			    cli.getConsole().err().println(MessageFormat.format(CLI.BUNDLE.getString("info.updated"), connection.getUri(false)));
		} else {
		    throw new IllegalArgumentException(CLI.BUNDLE.getString("error.onlyEditSingle"));
		}
		return 0;

	}
}
