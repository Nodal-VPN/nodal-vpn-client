package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.common.ConnectionStatus.Type;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "delete", usageHelpAutoWidth = true,  mixinStandardHelpOptions = true, description = "Delete connections.")
public class Delete extends AbstractConnectionCommand {

	@Parameters(description = "Connection names to delete.")
	private String[] names;

	@Override
	public Integer call() throws Exception {
	    
		var cli = getCLI();
        
		var pattern = getPattern(cli, names);
		var c = getConnectionsMatching(pattern, cli);
		var console = cli.getConsole();
		
		if (c.isEmpty()) {
			if (!cli.isQuiet()) {
				console.err().println(String.format("No connection matches %s", pattern));
				console.flush();
			}
			return 1;
		}
		for (var connection : c) {
			var status = Type.valueOf(connection.getStatus());
			if (status != Type.DISCONNECTED) {
				connection.disconnect("");
			}
			var uri = connection.getUri(true);
			connection.delete();
			if (!cli.isQuiet()) {
				console.out().println(String.format("Deleted %s", uri));
				console.flush();
			}
		}

		return 0;

	}
}
