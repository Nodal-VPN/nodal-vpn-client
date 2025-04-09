/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.logonbox.vpn.client.cli.commands;

import static com.jadaptive.nodal.core.lib.util.Util.toHumanSize;

import com.logonbox.vpn.client.cli.CLIContext;
import com.sshtools.jaul.ArtifactVersion;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "about", aliases = { "a", "ab" }, mixinStandardHelpOptions = true, description = "Show version information.")
public class About implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		var cli = (CLIContext) spec.parent().userObject();
		cli.initConsoleAndManager();
		cli.about();
		var console = cli.getConsole();
		var writer = console.out();
		if(ArtifactVersion.getVersion("com.jadaptive", "nodal-vpn-client-cli").indexOf("-SNAPSHOT") != -1) {
			cli.getVpnManager().getVpn().ifPresentOrElse(vpn -> {
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
