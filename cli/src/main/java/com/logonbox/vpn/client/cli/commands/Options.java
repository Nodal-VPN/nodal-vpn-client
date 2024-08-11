package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Optional;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Help.Ansi;

@Command(name = "option", aliases = { "option", "options", "config", "cfg", "configuration", "options" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "List, set or get global options.")
public class Options extends AbstractConnectionCommand {

	@Parameters(index = "0", description = "Name of option.", arity = "0..1")
	private Optional<String> name;

	@Parameters(index = "1", description = "Value of option.", arity = "0..1")
	private Optional<String> value;

	@Override
	public Integer call() throws Exception {
		var cli = getCLI();
		var console = cli.getConsole();
		if (name.isEmpty()) {
			for (String n : cli.getVpnManager().getVpnOrFail().getKeys()) {
			    writeDetail(console.out(), n, cli.getVpnManager().getVpnOrFail().getValue(n));
			}
		} else if (value.isEmpty()) {
			try {
				console.out().println(cli.getVpnManager().getVpnOrFail().getValue(name.get()));
			}
			catch(DBusExecutionException dee) {
				throw new IllegalArgumentException(MessageFormat.format(CLI.BUNDLE.getString("error.noSuchOption"), name.get()));
			}
		} else {
			cli.getVpnManager().getVpnOrFail().setValue(name.get(), value.get());
		}
		console.flush();
		return 0;
	}

    private void writeDetail(PrintWriter writer, String key,  String val) {
        writer.println(Ansi.AUTO.string(String.format(" %-22s @|faint :|@ @|bold %s|@", key, val)));
    }
}
