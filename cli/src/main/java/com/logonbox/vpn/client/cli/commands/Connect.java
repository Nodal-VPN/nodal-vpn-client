package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.dbus.VpnConnection;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "connect", usageHelpAutoWidth = true,  mixinStandardHelpOptions = true, description = "Connect a VPN.")
public class Connect extends AbstractConnectionCommand implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
	private String uri;

	@Override
	public Integer call() throws Exception {
		var cli = (CLIContext) spec.parent().userObject();
		var console = cli.getConsole();
		var out = console.out();
		var err = console.err();

		IVpnConnection connection = null;

		var pattern = getPattern(cli, new String[] { uri });
		var c = getConnectionsMatching(pattern, cli);
		if (c.isEmpty()) {
			if (Utils.isNotBlank(uri)) {
				if (!uri.startsWith("https://")) {
					if (uri.indexOf("://") != -1) {
						throw new IllegalArgumentException("Only HTTPS is supported.");
					}
					uri = "https://" + uri;
				}
				var uriObj = new URI(uri);
				if (!uriObj.getScheme().equals("https")) {
					throw new IllegalArgumentException("Only HTTPS is supported.");
				}

				var vpnManager = cli.getVpnManager();
                var vpn = vpnManager.getVpnOrFail();
                var connectionId = vpn.getConnectionIdForURI(uri);
				connection = connectionId < 0 ? null : vpn.getConnection(connectionId);

				if (connection == null) {
					connectionId = vpn.connect(uri);
					connection = vpn.getConnection(connectionId);
					if (!cli.isQuiet())
						out.println(String.format("Created new connection for %s", uri));
				}
			} else {
				throw new IllegalStateException("Connection information is required");
			}
		} else
			connection = c.get(0);
		
		console.flush();

		return doConnect(cli, out, err, connection);

	}

	Integer doConnect(CLIContext cli, PrintWriter out, PrintWriter err, IVpnConnection connection)
			throws DBusException, InterruptedException, IOException {
		var status = Type.valueOf(connection.getStatus());
		if (status == Type.DISCONNECTED) {
			try (var stateHelper = new StateHelper((VpnConnection) connection, cli.getVpnManager())) {
				if (!cli.isQuiet()) {
					out.println(String.format("Connecting to %s", connection.getUri(true)));
					cli.getConsole().flush();
				}
				stateHelper.on(Type.AUTHORIZING, (state, mode) -> {
					if(mode.equals(Mode.SERVICE)) {
						register(cli, connection, out, err);
					}
					else {
						throw new UnsupportedOperationException(
								String.format("This connection requires an authorization type, %s,  which is not currently supported by the CLI tools.", mode));
					}
				});
				stateHelper.start(Type.CONNECTING);
				connection.connect();
				try {
					status = stateHelper.waitForState(Type.CONNECTED, Type.DISCONNECTED);
					if (status == Type.CONNECTED) {
						if (!cli.isQuiet())
							out.println("Ready");
						cli.getConsole().flush();
						return 0;
					} else {
						if (!cli.isQuiet())
							err.println(String.format("Failed to connect to %s", connection.getUri(true)));
						cli.getConsole().flush();
						return 1;
					}
				}
				catch(Exception e) {
					disconnect(connection, cli);
					LoggerFactory.getLogger(AbstractConnectionCommand.class).info("Connection failed.", e);
					err.println(String.format("Failed to connect. %s", e.getMessage()));
					if(e.getMessage() == null)
						e.printStackTrace(err);
					cli.getConsole().flush();
					return 2;
				}
			}
		} else {
			err.println(String.format("Request to connect an already connected or connecting to %s",
					connection.getUri(true)));
			cli.getConsole().flush();
			return 1;
		}
	}
}
