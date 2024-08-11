package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.io.PrintWriter;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Parameters;

@Command(name = "show", aliases = { "s", "sh", "view" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Show connection details.")
public class Show extends AbstractConnectionCommand {

	@Parameters(description = "Show connection details.")
	private String[] names;

    @Option(names = { "-s", "--scripts" }, description = "Show contents of scripts as well.")
    private boolean showScripts;

	@Override
	public Integer call() throws Exception {
	    
		var cli = getCLI();
		var pattern = getPattern(cli, names);
		var console = cli.getConsole();
		
		for (var c : getConnectionsMatching(pattern, cli)) {
			printConnection(console.out(), c);
			console.out().println();
		}
		console.flush();
		return 0;
	}

	private void printConnection(PrintWriter writer, IVpnConnection connection) {
		writer.println(Ansi.AUTO.string("@|underline " + CLI.BUNDLE.getString("show.basic") + "|@"));
		writeDetail(writer, "connection", String.valueOf(connection.getId()));
        writeDetail(writer, "name", connection.getDisplayName());
        writeDetail(writer, "uri", connection.getUri(true));
        writeDetail(writer, "status", CLI.BUNDLE.getString("status." + connection.getStatus()));
        writeDetail(writer, "mode", connection.getMode());
        writeDetail(writer, "transient", connection.isTransient() ? CLI.BUNDLE.getString("show.yes") : CLI.BUNDLE.getString("show.no"));
        writeDetail(writer, "authorized", connection.isAuthorized() ? CLI.BUNDLE.getString("show.yes") : CLI.BUNDLE.getString("show.no"));
        writeDetail(writer, "shared", connection.isShared() ? CLI.BUNDLE.getString("show.yes") : CLI.BUNDLE.getString("show.no"));
        writeDetail(writer, "connectAtStartup", connection.isConnectAtStartup() ? CLI.BUNDLE.getString("show.yes") : CLI.BUNDLE.getString("show.no"));
        writeDetail(writer, "stayConnected", connection.isStayConnected() ? CLI.BUNDLE.getString("show.yes") : CLI.BUNDLE.getString("show.no"));
        writeDetail(writer, "owner", connection.getOwner());
		if(connection.isAuthorized()) {
	        writer.println(Ansi.AUTO.string("@|underline " + CLI.BUNDLE.getString("show.vpn") + "|@"));
	        writeDetail(writer, "interface", connection.getInterfaceName());
            writeDetail(writer, "address", connection.getAddress());
            writeDetail(writer, "endpoint", String.format("%s:%d", connection.getEndpointAddress(), connection.getEndpointPort()));
            writeDetail(writer, "dns", String.join(",", connection.getDns()));
			if(!connection.isRouteAll())
	            writeDetail(writer, "allowedIps", String.join(",", connection.getAllowedIps()));
	        writeDetail(writer, "routeAll", connection.isRouteAll() ? CLI.BUNDLE.getString("show.yes") : CLI.BUNDLE.getString("show.no"));
            writeDetail(writer, "userPublicKey", connection.getUserPublicKey());
            writeDetail(writer, "serverPublicKey", connection.getPublicKey());
            writeDetail(writer, "mtu", String.valueOf(connection.getMtu()));
            writeDetail(writer, "persistentKeepalive", String.valueOf(connection.getPersistentKeepalive()));
            if(showScripts) {
    			if(Utils.isNotBlank(connection.getPreUp()))
    	            writeDetail(writer, "preUp", connection.getPreUp());
    			if(Utils.isNotBlank(connection.getPostUp()))
                    writeDetail(writer, "postUp", connection.getPostUp());
    			if(Utils.isNotBlank(connection.getPreDown()))
                    writeDetail(writer, "preDown", connection.getPreDown());
    			if(Utils.isNotBlank(connection.getPostDown()))
                    writeDetail(writer, "postDown", connection.getPostDown());
            }
            else {
                writeDetail(writer, "scripts", Utils.isNotBlank(connection.getPreUp()) ||
                        Utils.isNotBlank(connection.getPreDown()) ||
                        Utils.isNotBlank(connection.getPostUp()) ||
                        Utils.isNotBlank(connection.getPostDown()) ? CLI.BUNDLE.getString("show.yes") : CLI.BUNDLE.getString("show.no"));
            }
		}
	}

	private void writeDetail(PrintWriter writer, String key,  String val) {
        writer.println(Ansi.AUTO.string(String.format(" %-22s @|faint :|@ @|bold %s|@", CLI.BUNDLE.getString("show." + key), val)));
    }
}
