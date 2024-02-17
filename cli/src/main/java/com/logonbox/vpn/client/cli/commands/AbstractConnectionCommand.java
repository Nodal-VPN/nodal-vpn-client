package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.ServiceClient;
import com.logonbox.vpn.client.common.ServiceClient.DeviceCode;
import com.logonbox.vpn.client.common.ServiceClient.NameValuePair;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.common.lbapi.InputField;
import com.logonbox.vpn.client.common.lbapi.LogonResult;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;

import jakarta.json.JsonObject;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

public abstract class AbstractConnectionCommand implements Callable<Integer>, IVersionProvider {
	static Logger log = LoggerFactory.getLogger(AbstractConnectionCommand.class);

	@Spec
	protected CommandSpec spec;

	protected AbstractConnectionCommand() {
	}

	protected CLIContext getCLI() {
		return (CLIContext) spec.parent().userObject();
	}

	@Override
    public String[] getVersion() throws Exception {
        return spec.parent().versionProvider().getVersion();
    }

    protected String getPattern(CLIContext cli, String... args) throws IOException {
		var console = cli.getConsole();
		String pattern = null;
		if (args == null || args.length == 0) {
            pattern = console.readLine(CLI.BUNDLE.getString("connection.enterId") + ": ");
		} else if (args.length > 0) {
			pattern = args[0];
		}
		return pattern;
	}

	protected boolean isSingleConnection(CLIContext cli) {
		return cli.getVpnManager().getVpnOrFail().getNumberOfConnections() == 1;
	}

	protected List<IVpnConnection> getConnectionsMatching(String pattern, CLIContext cli) {
		var l = new ArrayList<IVpnConnection>();
		try {
			var id = Long.parseLong(pattern);
			var connection = cli.getVpnManager().getVpnOrFail().getConnection(id);
			if (connection == null)
                throw new IllegalArgumentException(MessageFormat.format(CLI.BUNDLE.getString("connection.noConnection"), id));
			try {
				connection.getId();
			}
			catch(Exception e) {
                throw new IllegalArgumentException(MessageFormat.format(CLI.BUNDLE.getString("connection.noConnection"), id));
			}
			l.add(connection);
		} catch (NumberFormatException nfe) {
			for (var c : cli.getVpnManager().getVpnOrFail().getConnections()) {
				if (pattern == null || pattern.equals("") || c.getUri(true).matches(pattern)
						|| (c.getName() != null && c.getName().matches(pattern)) || c.getUri(true).matches(pattern)) {
					l.add(c);
				}
			}
		}
		return l;
	}

	protected void disconnect(IVpnConnection c, CLIContext cli)
			throws InterruptedException, IOException, DBusException {
		var console = cli.getConsole();
		if (!cli.isQuiet())
            console.out().println(MessageFormat.format(CLI.BUNDLE.getString("connection.disconnecting"), c.getUri(true)));
		console.flush();
		try (var helper = new StateHelper((VpnConnection) c, cli.getVpnManager())) {
			c.disconnect("");
			helper.waitForState(1, TimeUnit.MINUTES, Type.DISCONNECTED);
			if (!cli.isQuiet())
                console.out().println(CLI.BUNDLE.getString("connection.disconnected"));
		}
		console.flush();
	}

	protected void register(CLIContext cli, IVpnConnection connection, PrintWriter out, PrintWriter err)
			throws IOException, URISyntaxException {

		var sc = new ServiceClient(getCLI().getCookieStore(), new ServiceClient.Authenticator() {

			@Override
			public String getUUID() {
				return cli.getVpnManager().getVpnOrFail().getUUID();
			}

			@Override
			public HostnameVerifier getHostnameVerifier() {
				return cli.getCertManager();
			}

            @Override
            public void prompt(DeviceCode code) {
                out.println(CLI.BUNDLE.getString("connection.prompt"));
                out.println(code.verification_uri);
                out.println(MessageFormat.format(CLI.BUNDLE.getString("connection.userCode"), code.user_code));
                out.flush();
            }

            @Override
            public void error(JsonObject i18n, LogonResult logonResult) {
                if (logonResult.lastErrorIsResourceKey())
                    out.println(MessageFormat.format(CLI.BUNDLE.getString("connection.loginError"), i18n.getString(logonResult.errorMsg())));
                else
                    out.println(MessageFormat.format(CLI.BUNDLE.getString("connection.loginError"), logonResult.errorMsg()));
                out.flush();
            }

			@Override
			public void collect(JsonObject i18n, LogonResult result,
					Map<InputField, NameValuePair> results) throws IOException {
				out.println(MessageFormat.format(CLI.BUNDLE.getString("authenticationRequired"),
						i18n.getString("authentication." + result.formTemplate().resourceKey())));
				int fieldNo = 0;
				for (var field : result.formTemplate().inputFields()) {
				    var loop = true;
					while (loop) {
						switch (field.type()) {
						case select:
							out.println(field.label());
							int idx = 1;
							for (var opt : field.options()) {
								out.println(String.format("%d. %s",
										idx,
										opt.isNameResourceKey() ? i18n.getString(opt.name()) : opt.name()));
								idx++;
							}
							String reply;
							while(true) {
								reply = cli.getConsole().readLine("Option> ");
								if(reply.equals("")) {
									reply = field.defaultValue();
									break;
								}
								else {
									try {
										reply = field.options().get(Integer.parseInt(reply) -1).value();
										break;
									}
									catch(Exception e) {
									}
								}
							}
							results.put(field, new NameValuePair(field.resourceKey(), reply));
							break;
						case password:
							char[] pw = cli.getConsole().readPassword(field.label() + ": ");
							if (pw == null)
								throw new EOFException();
							if (pw.length > 0 || !field.required()) {
								results.put(field, new NameValuePair(field.resourceKey(), new String(pw)));
								loop = false;
							}
							break;
						case a:
                            if(field.label() == null)
                                out.println(field.defaultValue());
                            else
                                out.println(field.label() + ": " + field.defaultValue());
                            loop = false;
                            break;
						case text:
						case textarea:
							var input = cli.getConsole().readLine(field.label() + ": ");
							if (input == null)
								throw new EOFException();
							if (input.length() > 0 || !field.required()) {
								results.put(field, new NameValuePair(field.resourceKey(),
										input.equals("") ? field.defaultValue() : input));
								loop = false;
							} else if (input.length() == 0 && fieldNo == 0) {
								throw new IllegalStateException("Aborted.");
							}
							break;
						case hidden:
							results.put(field, new NameValuePair(field.resourceKey(), field.defaultValue()));
							break;
						case p:
						case div:
						case pre:
							out.println(field.defaultValue());
                            loop = false;
							break;
						case checkbox:
							if ("true".equals(field.defaultValue()))
								input = cli.getConsole().readLine(field.label() + " (Y)/N: ");
							else
								input = cli.getConsole().readLine(field.label() + " Y/(N): ");
							if (input == null)
								throw new EOFException();
							input = input.toLowerCase();
							if (input.equals("y") || input.equals("yes"))
								input = "true";
							else if (input.equals("n") || input.equals("no"))
								input = "false";
							else if (input.equals(""))
								input = field.defaultValue().equals("true") ? "true" : "false";
							else
								input = "";
							if (!input.equals("")) {
								results.put(field, new NameValuePair(field.resourceKey(), input));
								loop = false;
							}
							break;
						default:
							throw new UnsupportedOperationException(
									String.format("Field type %s is not supported.", field.type()));
						}
					}
					fieldNo++;
					out.flush();
				}

			}

            @Override
            public void authorized() throws IOException {
                out.println(CLI.BUNDLE.getString("connection.authorized"));
                cli.getConsole().flush();

            }
		}, getCLI().getCertManager());
		sc.register(connection);
	}
}
