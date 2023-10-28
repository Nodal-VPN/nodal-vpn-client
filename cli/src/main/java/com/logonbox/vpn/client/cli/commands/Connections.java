package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.drivers.lib.util.OsUtil;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "connections", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Display all information about all connections.")
public class Connections implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
	    
	    var cli = (CLIContext) spec.parent().userObject();
		var console = cli.getConsole();
		var writer = console.out();
		
		if(OsUtil.isAdministrator())  {
			writer.println(String.format("%5s %-25s %-15s %-5s %-10s %s", "ID", "Name", "Owner", "Flags", "Status", "URL"));
			writer.println("=======================================================================================================");
			for (var connection : cli.getVpnManager().getVpnOrFail().getConnections()) {
				writer.println(String.format("%5d %-25s %-15s %-5s %-10s %s", 
						connection.getId(),
						trimMax(connection.getDisplayName(), 25),
						trimMax(connection.getOwner(), 15),  
						getFlags(connection),
						trimMax(Type.valueOf(connection.getStatus()).name(), 10), 
						trimMax(connection.getUri(true), 15)));
			}
		}
		else {
			writer.println(String.format("%5s %-35s %-5s %-14s %s", "ID", "Name", "Flags", "Status", "URL"));
			writer.println("=======================================================================================");
			for (var connection : cli.getVpnManager().getVpnOrFail().getConnections()) {
				writer.println(String.format("%5d %-35s %-5s %-10s %s", 
						connection.getId(),
						trimMax(connection.getDisplayName(), 35), 
						getFlags(connection),
						trimMax(Type.valueOf(connection.getStatus()).name(), 10), 
						trimMax(connection.getUri(true), 21)));
			}
		}
		console.flush();

		return 0;
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
		if (str.length() > max - 2)
			return str.substring(0, max - 2) + "..";
		else
			return str;
	}
}
