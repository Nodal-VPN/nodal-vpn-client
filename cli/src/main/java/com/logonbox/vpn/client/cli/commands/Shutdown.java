package com.logonbox.vpn.client.cli.commands;

import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "shutdown", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Shutdown the service.")
public class Shutdown implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Option(names = { "-r", "--restart" }, description = "Restart.")
	private boolean delete;

	@Override
	public Integer call() throws Exception {
		var cli = (CLIContext) spec.parent().userObject();
		cli.getVPNOrFail().shutdown(false);
		cli.exitWhenDone();
		return 0;
	}
}
