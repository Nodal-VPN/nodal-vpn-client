package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;
import com.logonbox.vpn.client.common.Utils;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "config", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "List, set or get global configuration.")
public class Config extends AbstractConnectionCommand {

	@Parameters(description = "Name of configuration property.", arity = "0..1")
	private String name;

	@Parameters(description = "Value of configuration property.", arity = "0..1")
	private String value;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = getCLI();
		ConsoleProvider console = cli.getConsole();
		if (Utils.isBlank(name)) {
			for (String n : cli.getVpnManager().getVpnOrFail().getKeys()) {
				console.out().println(String.format("%-30s %s", n, cli.getVpnManager().getVpnOrFail().getValue(n)));
			}
		} else if (Utils.isBlank(value)) {
			try {
				console.out().println(cli.getVpnManager().getVpnOrFail().getValue(name));
			}
			catch(DBusExecutionException dee) {
				throw new IllegalArgumentException(String.format("No such configuration item %s", name));
			}
		} else {
			cli.getVpnManager().getVpnOrFail().setValue(name, value);
		}
		console.flush();
		return 0;
	}
}
