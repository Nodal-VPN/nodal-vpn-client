package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;

import java.text.MessageFormat;
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
            throw new IllegalArgumentException(MessageFormat.format(CLI.BUNDLE.getString("error.noMatch"), pattern));
		for (var connection : c) {
		    
			var wasTransient = connection.isTransient();
			var id = connection.save();
			
			if (wasTransient) {
				connection = cli.getVpnManager().getVpnOrFail().getConnection(id);
				if(cli.isVerbose()) {
				    console.out().println(MessageFormat.format(CLI.BUNDLE.getString("info.saved"), connection.getUri(true), connection.getId()));
					console.flush();
				}
			}
		}

		return null;

	}
}
