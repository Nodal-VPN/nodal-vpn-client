package com.logonbox.vpn.client.cli;

import com.logonbox.vpn.client.app.SimpleLoggingConfig;
import com.logonbox.vpn.client.cli.commands.About;
import com.logonbox.vpn.client.cli.commands.Change;
import com.logonbox.vpn.client.cli.commands.Connect;
import com.logonbox.vpn.client.cli.commands.Create;
import com.logonbox.vpn.client.cli.commands.Debug;
import com.logonbox.vpn.client.cli.commands.Disconnect;
import com.logonbox.vpn.client.cli.commands.ListConnections;
import com.logonbox.vpn.client.cli.commands.Options;
import com.logonbox.vpn.client.cli.commands.Remove;
import com.logonbox.vpn.client.cli.commands.Show;
import com.logonbox.vpn.client.cli.commands.Shutdown;
import com.logonbox.vpn.client.cli.commands.Update;
import com.logonbox.vpn.client.common.ClientPromptingCertManager;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.LoggingConfig.Audience;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.dbus.app.AbstractDBusApp;
import com.logonbox.vpn.client.dbus.client.DBusVpnManager;
import com.logonbox.vpn.drivers.lib.util.Util;
import com.sshtools.jaul.ArtifactVersion;

import org.slf4j.event.Level;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Command(name = CLI.COMMAND_NAME, versionProvider = CLI.VersionProvider.class, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Command line interface to the VPN Client service.", subcommands = {
		ListConnections.class, Connect.class, Create.class, Remove.class, Disconnect.class, Show.class,
		About.class, Change.class, Update.class, Debug.class, Options.class, Shutdown.class })
@Bundle
@Resource({"default-log4j-cli\\.properties"})
public class CLI extends AbstractDBusApp implements CLIContext {
    
    public final static String COMMAND_NAME = "jad-vpn";

    @Reflectable
	public final static class VersionProvider implements IVersionProvider {
	    
	    public VersionProvider() {} 
	    
        @Override
        public String[] getVersion() throws Exception {
            return new String[] {
                "CLI: " + ArtifactVersion.getVersion("com.logonbox", "client-logonbox-vpn-cli"),
                "DBus Java: " + ArtifactVersion.getVersion("com.github.hypfvieh", "dbus-java-core")
            };
        }
    }

	public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(CLI.class.getName());

	public static void main(String[] args) throws Exception {
		CLI cli = new CLI();
		try {
			System.exit(new CommandLine(cli).setExecutionExceptionHandler(new ExceptionHandler(() -> 
	            cli.verboseExceptions, ()-> cli.quietExceptions
	        , () -> CLI.COMMAND_NAME)).execute(args));
		} finally {
		    if(cli.console != null)
		        cli.console.out().flush();
		}
	}
 
	@Option(names = { "-v", "--verbose" }, description = "Be more verbose about current status and actions taken.")
	private boolean verbose;

    @Option(names = { "-X", "--verbose-exceptions" }, description = "Show verbose exception traces on errors.")
    private boolean verboseExceptions;

    @Option(names = { "-Q", "--quiet-exceptions" }, description = "Do not show any error messages, just return an error exit value.")
    private boolean quietExceptions;
	 
	@Spec
	private CommandSpec spec;

	private Thread awaitingServiceStop;
	private ConsoleProvider console;
	private boolean interactive = false;
	private Thread awaitingServiceStart;

	public CLI() {
		super();

	}

    @Override
	public boolean isVerbose() {
		return verbose;
	}

	@Override
	public ConsoleProvider getConsole() {
		return console;
	}

	@Override
	public void about() throws IOException {
		var console = getConsole();
		var writer = console.out();
		writer.println(String.format("CLI Version: %s", getVersion()));
        writer.println(String.format("DBus Version: %s", ArtifactVersion.getVersion("com.github.hypfvieh", "dbus-java-core")));
		getVpn().ifPresentOrElse(vpn -> {
			writer.println(String.format("Service Version: %s", vpn.getVersion()));
			writer.println(String.format("Device Name: %s", vpn.getDeviceName()));
			writer.println(String.format("Device UUID: %s", vpn.getUUID()));
		}, () -> writer.println("Service not available."));
		console.flush();
	}

	@Override
	protected int onCall() {
		try {
	        initConsoleAndManager();
			about();
            console.out().println();
	        CommandLine.usage(this, System.out);
			console.out().println();
			console.flush();
		} catch (Exception e1) {
			throw new IllegalStateException("Failed to open console.", e1);
		} finally {
	        shutdown(false);
		}
		
		return 0;
	}

	@Override
    public void initConsoleAndManager() {
        
        if(log != null) {
            return;
        }

        log = initApp();
        
        try {
            console = new NativeConsoleDevice();
        } catch (IllegalArgumentException iae) {
            console = new BufferedDevice();
        }
        
        getVpnManager().onVpnAvailable(() -> {
            log.info("Configuring Bus");
            giveUpWaitingForServiceStart();
        });
        getVpnManager().onVpnGone(() -> {
            if (awaitingServiceStop != null) {
                // Bridge lost as result of update, wait for it to come back
                giveUpWaitingForServiceStop();
                log.debug(String.format("Service stopped, awaiting restart"));
                awaitingServiceStart = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(30000);
                        } catch (InterruptedException e) {
                        }
                        if (awaitingServiceStop != null)
                            giveUpWaitingForServiceStart();
                    }
                };
                awaitingServiceStart.start();
            }
        });
        getVpnManager().start();
    }
	
    @Override
    protected LoggingConfig createLoggingConfig() {
        return new SimpleLoggingConfig(Level.WARN, Map.of(
                Audience.USER, "default-log-cli.properties",
                Audience.CONSOLE, "default-log-cli-console.properties",
                Audience.DEVELOPER, "default-log-cli-developer.properties"
        ));
    }

    @Override
    protected DBusVpnManager.Builder buildVpnManager(DBusVpnManager.Builder builder) {
        return builder.withAuthorization(true);
    }

    @Override
    protected void afterExit() {
        System.exit(0);
    }

	@Override
	protected PromptingCertManager createCertManager() {
		return new ClientPromptingCertManager(BUNDLE, this) {

			@Override
			public boolean isToolkitThread() {
				return true;
			}

			@Override
			public void runOnToolkitThread(Runnable r) {
				r.run();
			}

			@Override
			public boolean promptForCertificate(PromptType alertType, String title, String content, String key,
					String hostname, String message) {

				try {

					PrintWriter ou = console.out();
					ou.println(alertType.name());
					ou.println(Util.repeat(alertType.name().length(), '-'));
					ou.println();

					/* Title */
					if (Utils.isNotBlank(title)) {
						ou.println(title);
						ou.println(Util.titleUnderline(title.length()));
						ou.println();
					}

					/* Content */
					ou.println(MessageFormat.format(content, hostname, message));
					ou.println();

					console.flush();

					String yesAbbrev = BUNDLE.getString("certificate.yes");
					String noAbbrev = BUNDLE.getString("certificate.no");
					String saveAbbrev = BUNDLE.getString("certificate.save");

					String reply = console.readLine(BUNDLE.getString("certificate.prompt"), yesAbbrev,
							"(" + noAbbrev + ")", saveAbbrev);
					if (reply == null)
						throw new IllegalStateException("Aborted.");

					if (saveAbbrev.equalsIgnoreCase(reply)) {
						save(key);
						return true;
					} else if (yesAbbrev.equalsIgnoreCase(reply)) {
						return true;
					} else
						return false;
				} catch (IOException ioe) {
					throw new IllegalStateException("Failed to prompt for certificate.", ioe);
				}
			}

		};
	}

	@Override
	public boolean isInteractive() {
		return interactive;
	}

	private void giveUpWaitingForServiceStart() {
		if (awaitingServiceStart != null) {
			Thread t = awaitingServiceStart;
			awaitingServiceStart = null;
			t.interrupt();
		}
	}

	private void giveUpWaitingForServiceStop() {
		if (awaitingServiceStop != null) {
			Thread t = awaitingServiceStop;
			awaitingServiceStop = null;
			t.interrupt();
		}
	}

	@Override
	public String getVersion() {
		return app().map((app) -> app.asLocalApp().getVersion()).orElseGet(() -> ArtifactVersion.getVersion("com.logonbox", "client-logonbox-vpn-cli"));
	}

	@Override
	public boolean isConsole() {
		return true;
	}

    public static String link(String url, String text) {
        var ttype = System.getenv("TERM");
        if(ttype != null && ttype.startsWith("xterm")) {
            var astr = new StringBuilder();
            astr.append((char) 27 + "]8;;");
            astr.append(url);
            astr.append((char) 27 + "\\");
            astr.append(text);
            astr.append((char) 27 + "]8;;" + (char) 27 + "\\");
            return astr.toString();
        }
        else {
            return text;
        }
    }
}
