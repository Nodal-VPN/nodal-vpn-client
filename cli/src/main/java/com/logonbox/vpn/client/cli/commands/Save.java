package com.logonbox.vpn.client.cli.commands;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "save", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Save a temporary connection as a permanent one.")
public class Save extends AbstractConnectionCommand implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Parameters(description = "Connection names to save.")
	private String[] names;

	@Override
	public Integer call() throws Exception {
	    
		var cli = getCLI();
		var pattern = getPattern(cli, names);
		var console = cli.getConsole();
		var c = getConnectionsMatching(pattern, cli);
		
		if (c.isEmpty())
			throw new IllegalArgumentException(String.format("No connection matches %s", pattern));
		for (var connection : c) {
		    
			var wasTransient = connection.isTransient();
			var id = connection.save();
			
			if (wasTransient) {
				connection = cli.getVpnManager().getVPNConnection(id);
				if(!cli.isQuiet()) {
					console.out()
							.println(String.format("Saved %s, ID is now %d", connection.getUri(true), connection.getId()));
					console.flush();
				}
			}
		}

		return null;

	}
}
