package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.cli.CLIContext;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "update", aliases = { "up", "upd", "upgrade" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Update the client.")
public class Update implements Callable<Integer> {

	@Spec
	private CommandSpec spec;
	
	@Option(names = { "y", "yes", "-y", "--yes" })
	private boolean yes;
	
	@Option(names = { "c", "check", "-c", "--check" })
	private boolean checkOnly;
	
	@Override
	public Integer call() throws Exception {
		var cli = (CLIContext) spec.parent().userObject();
        cli.initConsoleAndManager();
        
		var console = cli.getConsole();
		var writer = console.out();
		cli.getUpdateService().checkForUpdate();
		if(cli.getUpdateService().isNeedsUpdating()) {
			if(checkOnly) {
			    if(cli.isVerbose())
			        writer.println(MessageFormat.format(CLI.BUNDLE.getString("info.availableUpdate"), cli.getUpdateService().getAvailableVersion(), cli.getUpdateService().getContext().getPhase()));
				console.flush();
				return 0;
			}
			else if(!yes) {
				String answer = console.readLine("Version %s available. Update? (Y)/N: ", cli.getUpdateService().getAvailableVersion()).toLowerCase();
				if(!answer.equals("") && !answer.equals("y") && !answer.equals("yes")) {
				    throw new IOException(CLI.BUNDLE.getString("error.abortedUpdate"));
				}
			}
		}
		else {
	        throw new IllegalStateException(MessageFormat.format(CLI.BUNDLE.getString("info.upToDate"), cli.getUpdateService().getContext().getVersion(), cli.getUpdateService().getContext().getPhase()));
		}
		
		cli.getUpdateService().update();
		return 0;
	}
}
