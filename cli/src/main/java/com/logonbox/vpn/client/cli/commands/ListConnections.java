package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.drivers.lib.util.OsUtil;

import java.util.Arrays;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "list", aliases = { "ls", "lst", "li", "l" }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Display all information about all connections.")
public class ListConnections implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

    @Option(names = { "-l", "--long" }, negatable = true, description = "Long format output, additional details are displayed.")
    private boolean longFormat;

    @Option(names = { "-h", "--headings" }, negatable = true, description = "Show headings.")
    private boolean headings;

    @Option(names = { "-n", "--sort-name" }, negatable = true, description = "Sort by name rather than ID.")
    private boolean sortName;

	@Override
	public Integer call() throws Exception {
	    
	    var cli = (CLIContext) spec.parent().userObject();
        cli.initConsoleAndManager();
        
		var console = cli.getConsole();
		var writer = console.out();
        var connections = Arrays.asList(cli.getVpnManager().getVpnOrFail().getConnections()).stream().sorted((o1, o2) -> {
            if(sortName) {
                return o1.getName().compareTo(o2.getName());
            }
            else {
                return Long.valueOf(o1.getId()).compareTo(o2.getId());
            }
        }).toList();
        
        if(connections.isEmpty()) {
            throw new IllegalStateException(CLI.BUNDLE.getString("error.noConnections"));
        }
        
        var width = spec.commandLine().getUsageHelpWidth(); 
        
		if(!OsUtil.isAdministrator())  {
		    if(longFormat) {
                var allExceptUrl = 69;
                var remain = width - allExceptUrl;
                if(headings)
                    writer.println(Ansi.AUTO.string(String.format("@|underline %5s %-20s %-15s %-5s %-13s %-" + remain + "s|@", "ID", "Name", "Owner", "Flags", "Status", "URL")));
                for (var connection : connections) {
    				writer.println(String.format("%5d %-20s %-15s %-5s %-13s %-" + remain + "s", 
    						connection.getId(),
    						trimMax(connection.getDisplayName(), 20),
    						trimMax(connection.getOwner(), 15),  
    						getFlags(connection),
    						trimMax(CLI.BUNDLE.getString("status." + connection.getStatus()), 13), 
    						trimMax(connection.getUri(true), remain)));
    			}
		    }
		    else {
                for (var connection : connections) {
                    var t = Type.valueOf(connection.getStatus());
                    var ownerStr = "@|faint " + connection.getOwner() + "|@";
                    writer.println((t.isSuccess() ? "*" : "") + typeToStyleString(t, "%s", connection.getDisplayName()) + " " + Ansi.AUTO.string(ownerStr));
                }
		    }
		}
		else {
            if(longFormat) {
                var allExceptUrl = 59;
                var remain = width - allExceptUrl;
                if(headings)
                    writer.println(Ansi.AUTO.string(String.format("@|underline %5s %-35s %-5s %-13s %-" + remain + "s|@", "ID", "Name", "Flags", "Status", "URL")));
    			for (var connection : connections) {
    				writer.println(String.format("%5d %-35s %-5s %-13s %-" + remain + "s", 
    						connection.getId(),
    						trimMax(connection.getDisplayName(), 32), 
    						getFlags(connection),
    						trimMax(CLI.BUNDLE.getString("status." + connection.getStatus()), 13), 
    						trimMax(connection.getUri(true), remain)));
    			}
            }
            else {
                for (var connection : connections) {
                    var t = Type.valueOf(connection.getStatus());
                    writer.println((t.isSuccess() ? "*" : "") + typeToStyleString(t, "%s", connection.getDisplayName()));
                }
            }
		}
		console.flush();

		return 0;
	}
	
	private String typeToStyleString(Type type, String fmt, Object... args) {
	    if(type.isSuccess()) {
	        return Ansi.AUTO.string("@|green " + String.format(fmt, args) + "|@");
        }
        else if(type.isWarn()) {
            return Ansi.AUTO.string("@|yellow " + String.format(fmt, args) + "|@");
        }
        else if(type.isPrompt()) {
            /* Haha! */
            return Ansi.AUTO.string("@|blink " + String.format(fmt, args) + "|@");
        }
        else {
            return String.format(fmt, args);
        } 
	}

	private String getFlags(IVpnConnection connection) {
		String flags = "";
		if(connection.isTransient())
			flags += "T";
		if(connection.isConnectAtStartup())
			flags += "S";
		if(connection.isStayConnected())
			flags += "C";
		if(connection.isAuthorized())
			flags += "A";
		if(connection.isShared())
			flags += "O";
		return flags;
	}

	private static String trimMax(String str, int max) {
		if (str.length() > max)
			return str.substring(0, max - 2) + "..";
		else
			return str;
	}
}
