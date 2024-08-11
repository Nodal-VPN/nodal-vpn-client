package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.text.MessageFormat;
import java.util.ArrayList;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "disconnect", aliases = { "dis", "di", "d", "dc", "close" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Disconnect from a VPN.")
public class Disconnect extends AbstractConnectionCommand {

	@Parameters(description = "Connection names, URLs or IDs to disconnect. If none supplied, all connections will be disconnected.")
	private String[] names;

	@Option(names = { "-d", "--delete" }, description = "Permanently delete the connection as well.")
	private boolean delete;

	@Override
	public Integer call() throws Exception {

		var cli = getCLI();
        var console = cli.getConsole();
        var l = new ArrayList<IVpnConnection>();
        
		if ((names == null || names.length == 0)) {
		    for(var c : cli.getVpnManager().getVpnOrFail().getConnections()) {
                var statusType = Type.valueOf(c.getStatus());
                if (statusType.isDisconnectable()) {
                    l.add(c);
                }
            }
		}
		else {    
			var pattern = getPattern(cli, names);
			for (var c : getConnectionsMatching(pattern, cli)) {
				var statusType = Type.valueOf(c.getStatus());
                if (statusType.isDisconnectable()) {
                    l.add(c);
				} 
			}
		}
		
		if(l.isEmpty()) {
		    throw new IllegalStateException(CLI.BUNDLE.getString("error.noActiveConnections"));
		}
		
		for(var c : l) {
    		disconnect(c, cli);
            if (delete) {
                c.delete();
                if (cli.isVerbose()) {
                    console.out().println(MessageFormat.format(CLI.BUNDLE.getString("info.deleted"), c.getUri(true)));
                    console.flush();
                }
            }
		}
		
		return 0;
	}

}
