/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
