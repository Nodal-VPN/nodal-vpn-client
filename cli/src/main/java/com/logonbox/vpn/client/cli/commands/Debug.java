package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.common.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "debug", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Show debug information.")
public class Debug implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		var cli = (CLIContext) spec.parent().userObject();
        cli.initConsoleAndManager();
        
		var console = cli.getConsole();
		var out = console.out();
		out.println("Environment:");
		for(var ee : System.getenv().entrySet()) {
			out.println(String.format("  %s=%s", ee.getKey(), Utils.abbreviate(ee.getValue(), 40)));
		}
		out.println();
		out.println("System:");
		for(var ee : System.getProperties().entrySet()) {
			out.println(String.format("  %s=%s", ee.getKey(), Utils.abbreviate((String)ee.getValue(), 40)));
		}
		out.println();
		out.println("TTY:");
		out.println("   stty: " + cmd("stty", "-g"));
		out.println("   tty: " + cmd("tty"));
		console.flush();
		return 0;
	}
	
	protected String cmd(String... args) throws IOException {
		var b = new ProcessBuilder(args);
		b.inheritIO();
		b.redirectErrorStream(true);
		var p = b.start();
		try(var out = new ByteArrayOutputStream()) {
			try(InputStream in = p.getInputStream()) {
				in.transferTo(out);
			}
			try {
				p.waitFor();
			} catch (InterruptedException e) {
				throw new IOException("Interrupted.", e);
			}
			return new String(out.toByteArray()).trim();
		}
	}
}
