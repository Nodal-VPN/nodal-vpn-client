package com.logonbox.vpn.client.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "edit", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Edit connections.")
public class Edit extends AbstractConnectionCommand {

	@Parameters(description = "Connection name to edit.")
	private String name;

	@Option(names = { "-n", "--name" }, description = "Set the name.")
	private String newName;

	@Option(names = { "-u", "--uri" }, description = "Set the URI.")
	private String uri;

	@Option(names = { "-s",
			"--connect-at-startup" }, negatable = true, description = "Set the connect at startup option.")
	private Boolean connectAtStartup;

	@Option(names = { "-c",
			"--stay-connected" }, negatable = true, description = "Set the stay connected option.")
	private Boolean stayConnected;

	@Override
	public Integer call() throws Exception {
		var cli = getCLI();
        
		var pattern = getPattern(cli, name);

		var c = getConnectionsMatching(pattern, cli);
		if (c.isEmpty()) {
			if (!cli.isQuiet())
				cli.getConsole().err().println(String.format("No connection matches %s", pattern));
			return 1;
		} else if (c.size() == 1) {
			var connection = c.get(0);
			connection.update(newName == null ? connection.getName() : newName, uri == null ? connection.getUri(false) : uri,
					connectAtStartup == null ? connection.isConnectAtStartup() : connectAtStartup, stayConnected == null ? connection.isStayConnected() : stayConnected);
			if (!cli.isQuiet())
				cli.getConsole().err().println(String.format("Updated %s", connection.getUri(false)));
		} else {
			if (!cli.isQuiet())
				cli.getConsole().err().println("May only edit a single connection.");
			return 1;
		}
		return 0;

	}
}
