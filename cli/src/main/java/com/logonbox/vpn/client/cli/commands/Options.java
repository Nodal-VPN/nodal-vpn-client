package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.common.Utils;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

import java.text.MessageFormat;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "option", aliases = { "option", "options", "config", "cfg", "configuration", "options" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "List, set or get global options.")
public class Options extends AbstractConnectionCommand {

	@Parameters(description = "Name of option.", arity = "0..1")
	private String name;

	@Parameters(description = "Value of option.", arity = "0..1")
	private String value;

	@Override
	public Integer call() throws Exception {
		var cli = getCLI();
		var console = cli.getConsole();
		if (Utils.isBlank(name)) {
			for (String n : cli.getVpnManager().getVpnOrFail().getKeys()) {
				console.out().println(String.format("%-30s %s", n, cli.getVpnManager().getVpnOrFail().getValue(n)));
			}
		} else if (Utils.isBlank(value)) {
			try {
				console.out().println(cli.getVpnManager().getVpnOrFail().getValue(name));
			}
			catch(DBusExecutionException dee) {
				throw new IllegalArgumentException(MessageFormat.format(CLI.BUNDLE.getString("error.noSuchOption"), name));
			}
		} else {
			cli.getVpnManager().getVpnOrFail().setValue(name, value);
		}
		console.flush();
		return 0;
	}
}
