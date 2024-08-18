package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.drivers.lib.util.OsUtil;

import java.util.Arrays;
import java.util.List;
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

    @Option(names = { "-a", "--all" }, negatable = true, description = "Show all connections including those not owned by you (administrator only).")
    private boolean all;

	@Override
	public Integer call() throws Exception {
	    
	    var cli = (CLIContext) spec.parent().userObject();
        cli.initConsoleAndManager();
        
		var console = cli.getConsole();
		var writer = console.out();
        
        var width = spec.commandLine().getUsageHelpWidth(); 
        
		if(all)  {
		    if(!OsUtil.isAdministrator())
		        throw new IllegalStateException(CLI.BUNDLE.getString("error.notAdministrator"));
	        var connections = sortConnections(cli, cli.getVpnManager().getVpnOrFail().getAllConnections());
		    if(longFormat) {
                var allExceptUrl = 44;
                var remain = ( width - allExceptUrl );
                var left = (int)(remain * 0.33);
                var right= (int)(remain * 0.66);
                if(headings)
                    writer.println(Ansi.AUTO.string(String.format("@|underline %5s %-" + left + "s %-15s %-5s %-13s %-" + right + "s|@", "ID", "Name", "Owner", "Flags", "Status", "URL")));
                for (var connection : connections) {
                    var t = Type.valueOf(connection.getStatus());
    				writer.println(typeToStyleString(t, "%5d %-" + left + "s %-15s %-5s %-13s %-" + right + "s", 
    						connection.getId(),
    						trimMax(connection.getDisplayName(), left),
    						trimMax(connection.getOwner(), 15),  
    						getFlags(connection),
    						trimMax(CLI.BUNDLE.getString("status." + connection.getStatus()), 13), 
    						trimMax(connection.getUri(true), right)));
    			}
		    }
		    else {
                for (var connection : connections) {
                    var t = Type.valueOf(connection.getStatus());
                    var ownerStr = "@|faint " + connection.getOwner() + "|@";
                    writer.println(typeToStyleString(t, "%s", connection.getDisplayName()) + " " + Ansi.AUTO.string(ownerStr) + typeToIndicatorString(t));
                }
		    }
		}
		else {
	        var connections = sortConnections(cli, cli.getVpnManager().getVpnOrFail().getConnections());
            if(longFormat) {
                var allExceptUrl = 28;
                var remain = ( width - allExceptUrl );
                var left = (int)(remain * 0.33);
                var right= (int)(remain * 0.66);
                if(headings)
                    writer.println(Ansi.AUTO.string(String.format("@|underline %5s %-" + left + "s %-5s %-13s %-" + right + "s|@", "ID", "Name", "Flags", "Status", "URL")));
    			for (var connection : connections) {
                    var t = Type.valueOf(connection.getStatus());
    				writer.println(typeToStyleString(t, "%5d %-" + left + "s %-5s %-13s %-" + right + "s", 
    						connection.getId(),
    						trimMax(connection.getDisplayName(), left), 
    						getFlags(connection),
    						trimMax(CLI.BUNDLE.getString("status." + connection.getStatus()), 13), 
    						trimMax(connection.getUri(true), right)));
    			}
            }
            else {
                for (var connection : connections) {
                    var t = Type.valueOf(connection.getStatus());
                    writer.println(typeToStyleString(t, "%s", connection.getDisplayName()) + typeToIndicatorString(t));
                }
            }
		}
		console.flush();

		return 0;
	}

    protected List<VpnConnection> sortConnections(CLIContext cli, VpnConnection[] connections ) {
        var sortedConnections = Arrays.asList(connections).stream().sorted((o1, o2) -> {
            if(sortName) {
                return o1.getName().compareTo(o2.getName());
            }
            else {
                return Long.valueOf(o1.getId()).compareTo(o2.getId());
            }
        }).toList();
        
        if(sortedConnections.isEmpty()) {
            throw new IllegalStateException(CLI.BUNDLE.getString("error.noConnections"));
        }
        return sortedConnections;
    }
	
	private String typeToStyleString(Type type, String fmt, Object... args) {
	    if(type.isSuccess()) {
	        return Ansi.AUTO.string("@|green " + String.format(fmt, args) + "|@");
        }
        else if(type.isWarn() || type.isPrompt()) {
            return Ansi.AUTO.string("@|yellow " + String.format(fmt, args) + "|@");
        }
        else {
            return String.format(fmt, args);
        } 
	}
    
    private String typeToIndicatorString(Type type) {
        if(type.isSuccess()) {
            return Ansi.AUTO.string("*");
        }
        else if(type.isWarn()) {
            return Ansi.AUTO.string("@|blink !|@");
        }
        else if(type.isPrompt()) {
            /* Haha! */
            return Ansi.AUTO.string("@|blink ?|@");
        }
        else {
            return "";
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
