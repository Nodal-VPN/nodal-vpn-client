package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.common.Utils;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "help", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, helpCommand = true, description = "Shows help.")
public class Help implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Parameters(index = "0", arity = "0..1", description = "Displays help for a particular command.")
	private String command;

	@Override
	public Integer call() throws Exception {
		var cli = (CLIContext) spec.parent().userObject();
        cli.initConsoleAndManager();
		var console = cli.getConsole();
		var out = console.out();
		if(Utils.isNotBlank(command)) {
			var cmd = spec.parent().subcommands().get(command);
			if(cmd == null)
				throw new IllegalArgumentException("No such command.");
			out.println(cmd.getUsageHelpWidth());
		}
		else {
			var parentSubcommands = spec.parent().subcommands();
			for (var en : parentSubcommands.entrySet()) {
				out.println(String.format("%-12s %s", en.getKey(), en.getValue().getHelp().description().trim()));
			}
		}
		console.flush();
		return 0;
	}
}
