package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLIContext;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "exit", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Exit interactive shell.")
public class Exit implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		var cli = (CLIContext) spec.parent().userObject();
        cli.initConsoleAndManager();
		if(!cli.isQuiet()) {
			var console = cli.getConsole();
			console.out().println("Goodbye!");
			console.flush();
		}
		cli.exitWhenDone();
		return 0;
	}
}
