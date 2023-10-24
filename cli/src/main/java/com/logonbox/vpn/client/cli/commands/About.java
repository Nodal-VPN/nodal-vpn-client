package com.logonbox.vpn.client.cli.commands;

import static com.logonbox.vpn.drivers.lib.util.Util.toHumanSize;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.common.AppVersion;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "about", mixinStandardHelpOptions = true, description = "Show version information.")
public class About implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		var cli = (CLIContext) spec.parent().userObject();
		cli.about();
		var console = cli.getConsole();
		var writer = console.out();
		if(AppVersion.getVersion("com.logonbox/client-logonbox-vpn-cli").indexOf("-SNAPSHOT") != -1) {
			cli.getVpnManager().getVPN().ifPresentOrElse(vpn -> {
				var vpnFreeMemory = vpn == null ? 0 : vpn.getFreeMemory();
				var vpnMaxMemory = vpn == null ? 0 : vpn.getMaxMemory();
				var vpnUsedMemory = vpnMaxMemory - vpnFreeMemory;
				
				writer.println();
				writer.println("Service Memory");
				writer.println("==============`");
				writer.println(String.format("Max Memory: %s", toHumanSize(vpnMaxMemory)));
				writer.println(String.format("Free Memory: %s", toHumanSize(vpnFreeMemory)));
				writer.println(String.format("Used Memory: %s", toHumanSize(vpnUsedMemory)));
			}, () -> writer.println("Service not available."));
			writer.println();
			
			var freeMemory = Runtime.getRuntime().freeMemory();
			var maxMemory = Runtime.getRuntime().maxMemory();
			var usedMemory = maxMemory - freeMemory;
			
			writer.println("CLI Memory");
			writer.println("==========");
			writer.println(String.format("Max Memory: %s", toHumanSize(maxMemory)));
			writer.println(String.format("Free Memory: %s", toHumanSize(freeMemory)));
			writer.println(String.format("Used Memory: %s", toHumanSize(usedMemory)));
			
		}
		console.flush();
		
		return 0;
	}
}
