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
