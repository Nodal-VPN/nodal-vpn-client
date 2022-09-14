package com.logonbox.vpn.client.cli.commands;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "config", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "List, set or get global configuration.")
public class Config extends AbstractConnectionCommand {

	@Parameters(description = "Name of configuration property.", arity = "0..1")
	private String name;

	@Parameters(description = "Value of configuration property.", arity = "0..1")
	private String value;

	@Option(names = { "-R",
			"--reset" }, negatable = true, description = "Reset value.")
	private boolean reset;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = getCLI();
		ConsoleProvider console = cli.getConsole();
		if (StringUtils.isBlank(name)) {
			for (String n : cli.getVPN().getKeys()) {
				console.out().println(String.format("%-30s %s", n, cli.getVPN().getValue(n)));
			}
		} else if (StringUtils.isBlank(value)) {
			try {
				if(reset)
					cli.getVPN().resetValue(name);
				else
					console.out().println(cli.getVPN().getValue(name));
			}
			catch(DBusExecutionException dee) {
				throw new IllegalArgumentException(String.format("No such configuration item %s", name));
			}
		} else {
			cli.getVPN().setValue(name, value);
		}
		console.flush();
		return 0;
	}
}
