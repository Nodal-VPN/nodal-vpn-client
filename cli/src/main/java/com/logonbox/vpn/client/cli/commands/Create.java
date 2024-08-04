package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.ConnectionUtil;
import com.logonbox.vpn.client.common.dbus.VpnConnection;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "create", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Create a new VPN connection.")
public class Create extends AbstractConnectionCommand implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Option(names = { "-b", "--background" }, description = "Connect in the background and return immediately.")
	private boolean background;

	@Option(names = { "-c",
			"--connect-at-startup" }, description = "Have this connection activate when the service starts.", defaultValue = "true")
	private boolean connectAtStartup = true;

	@Option(names = { "-s", "--stay-connected" }, description = "Stay connected.", defaultValue = "true")
	private boolean stayConnected = true;

	@Option(names = { "-n", "--dont-connect-now" }, description = "Just create the connection, don't connect yet.")
	private boolean dontConnectNow;

	@Option(names = { "-m", "--mode" }, description = "The mode of connection.")
	private Mode mode = Mode.CLIENT;

	@Parameters(index = "0", description = "The URI of the server to connect to. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path].")
	private String uri;

	@Override
	public Integer call() throws Exception {
		var uriObj = ConnectionUtil.getUri(uri);
		if (!uriObj.getScheme().equals("https")) {
			throw new IllegalArgumentException("Only HTTPS is supported.");
		}

		var cli = getCLI();
        
		var vpnManager = cli.getVpnManager();
        var connectionId = vpnManager.getVpnOrFail().getConnectionIdForURI(uriObj.toASCIIString());
		var console = cli.getConsole();
		var err = console.err();
		var out = console.out();
		
		if (connectionId > 0) {
			if (!cli.isQuiet())
				err.println(String.format("Connection for %s already exists", uriObj));
			console.flush();
			return 1;
		}

		connectionId = vpnManager.getVpnOrFail().createConnection(uriObj.toASCIIString(), connectAtStartup, stayConnected, mode.name());
		if (!dontConnectNow) {
		    var vpn = vpnManager.getVpnOrFail();
			var connection = vpn.getConnection(connectionId);
			if (background) {
				connection.connect();
			} else {
				try (var stateHelper = new StateHelper((VpnConnection) connection, cli.getVpnManager())) {
					stateHelper.on(Type.AUTHORIZING, (state, mode) -> {
						if (mode.equals(Mode.SERVICE)) {
							register(cli, connection, out, err);
						} else {
							throw new UnsupportedOperationException(String.format(
									"This connection requires an authorization type, %s,  which is not currently supported by the CLI tools.",
									mode));
						}
					});
					stateHelper.start(Type.CONNECTING);
					connection.connect();
					var status = stateHelper.waitForState(Type.DISCONNECTED, Type.CONNECTED);
					if (status == Type.CONNECTED) {
						if (!cli.isQuiet()) {
							out.println("Ready");
							console.flush();
						}
						return 0;
					} else {
						if (!cli.isQuiet()) {
							err.println(String.format("Failed to connect to %s", connection.getUri(true)));
							console.flush();
						}
						return 1;
					}
				}
			}
		}

		return 0;
	}
}
