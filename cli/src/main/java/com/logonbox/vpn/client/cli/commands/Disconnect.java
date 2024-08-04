package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.common.ConnectionStatus.Type;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "disconnect", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Disconnect from a VPN.")
public class Disconnect extends AbstractConnectionCommand {

	@Parameters(description = "Connection names to delete.")
	private String[] names;

	@Option(names = { "-d", "--delete" }, description = "Permanently delete the connection as well.")
	private boolean delete;

	@Override
	public Integer call() throws Exception {

		var cli = getCLI();
        
		if ((names == null || names.length == 0) && isSingleConnection(cli)) {
			disconnect(cli.getVpnManager().getVpnOrFail().getConnections()[0], cli);
		} else {
			var pattern = getPattern(cli, names);
			for (var c : getConnectionsMatching(pattern, cli)) {
				var statusType = Type.valueOf(c.getStatus());
				var console = cli.getConsole();
				if (statusType != Type.DISCONNECTED && statusType != Type.DISCONNECTING) {
					disconnect(c, cli);
				} else {
					if (!cli.isQuiet()) {
						console.err().println(String.format("%s is not connected.", c.getUri(true)));
						console.flush();
					}
				}

				if (delete) {
					c.delete();
					if (!cli.isQuiet()) {
						console.out().println(String.format("Deleted %s", c.getUri(true)));
						console.flush();
					}
				}
			}
		}
		return 0;
	}

}
