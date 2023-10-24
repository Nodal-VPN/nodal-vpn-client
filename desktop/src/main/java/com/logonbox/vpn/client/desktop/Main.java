package com.logonbox.vpn.client.desktop;

import com.install4j.api.launcher.StartupNotification;
import com.install4j.api.launcher.StartupNotification.Listener;
import com.logonbox.vpn.client.common.ClientPromptingCertManager;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.NoUpdateService;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.UpdateService;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.dbus.AbstractDBusClient;
import com.logonbox.vpn.client.gui.jfx.AppContext;
import com.logonbox.vpn.client.gui.jfx.Configuration;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.logonbox.vpn.client.logging.SimpleLogger;
import com.logonbox.vpn.client.logging.SimpleLoggerConfiguration;
import com.sshtools.liftlib.OS;
import  com.sun.jna.platform.win32.Advapi32Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

//import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Callable;

//import javax.swing.UIManager;
//import javax.swing.UnsupportedLookAndFeelException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "logonbox-vpn-gui", mixinStandardHelpOptions = true, description = "Start the LogonBox VPN graphical user interface.")
public class Main extends AbstractDBusClient implements Callable<Integer>, Listener, AppContext {
	static Logger log;

	public final static String SID_ADMINISTRATORS_GROUP = "S-1-5-32-544";

	/**
	 * Used to get version from Maven meta-data
	 */
	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-gui-jfx";

	private static Main instance;

	@Option(names = { "-C", "--no-close" }, description = "Do not allow the window to be closed manually.")
	private boolean noClose;

	@Option(names = { "-x", "--exit-on-connection" }, description = "Exit on successful connection.")
	private boolean exitOnConnection;

	@Option(names = { "-A", "--no-add-when-no-connections" }, description = "Do not show the Add connection page when no connections are configured.")
	private boolean noAddWhenNoConnections;

	@Option(names = { "-M", "--no-minimize" }, description = "Do not allow the window to be minimized manually.")
	private boolean noMinimize;

	@Option(names = { "-S",
			"--no-systray" }, description = "Do not start the system tray, the application will immediately exit when the window is closed.")
	private boolean noSystemTray;

	@Option(names = { "-B", "--no-sidebar" }, description = "Do not show the burger menu or side menu.")
	private boolean noSidebar;

	@Option(names = { "-R", "--no-resize" }, description = "Do not allow the window to be resized.")
	private boolean noResize;

	@Option(names = { "-D",
			"--no-drag" }, description = "Do not allow the window to be moved. Will be centred on primary monitor.")
	private boolean noMove;

	@Option(names = { "-s", "--size" }, description = "Size of window, in the format <width>X<height>.")
	private String size;

	@Option(names = { "-T", "--always-on-top" }, description = "Keep the window on top of others.")
	private boolean alwaysOnTop;

	@Option(names = { "-U", "--no-updates" }, description = "Do not perform any updates.")
	private boolean noUpdates;

	@Option(names = { "-c", "--connect" }, description = "Connect to the first available pre-configured connection.")
	private boolean connect;

	@Option(names = { "-n", "--create" }, description = "Create a new connection if one with the provided URI does not exist (requires URI parameter).")
	private boolean createIfDoesntExist;

	@Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
	private String uri;

	private static Optional<UIContext<?>> uiContext = Optional.empty();

	public Main() {
		super();
		instance = this;
		setSupportsAuthorization(true);

//		if(Taskbar.isTaskbarSupported()) {
//	        try {
//	    		final Taskbar taskbar = Taskbar.getTaskbar();
//	    		if(OS.isMacOs())
//		            taskbar.setIconImage(java.awt.Toolkit.getDefaultToolkit()
//							.getImage(Main.class.getResource("mac-logo128px.png")));
//	    		else
//		            taskbar.setIconImage(java.awt.Toolkit.getDefaultToolkit()
//							.getImage(Main.class.getResource("logonbox-icon128x128.png")));
//	        } catch (final UnsupportedOperationException e) {
//	        } catch (final SecurityException e) {
//	        }
//		}

		System.setProperty(SimpleLoggerConfiguration.CONFIGURATION_FILE_KEY, "default-log-gui.properties");

		String cfgLevel = Configuration.getDefault().logLevelProperty().get();
		if(Utils.isNotBlank(cfgLevel)) {
			try {
				setLevel(Level.valueOf(cfgLevel));
			}
			catch(IllegalArgumentException iae) {
			}
		}
		log = LoggerFactory.getLogger(Main.class);

		log.info(String.format("LogonBox VPN Client GUI, version %s", AppVersion.getVersion(ARTIFACT_COORDS)));
		log.info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")"));
		try {
			log.info(String.format("CWD: %s", new File(".").getCanonicalPath()));
		} catch (IOException e) {
		}

		StartupNotification.registerStartupListener(this);
	}

	@Override
	public void setLevel(Level level) {
	    System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, level.toString());
	}
	
	@Override
	public Integer call() throws Exception {
		Application.launch(Client.class, new String[0]);
		return 0;
	}

	@Override
	public Level getDefaultLogLevel() {
		return Level.WARN;
	}

	@Override
	public boolean isNoAddWhenNoConnections() {
		return noAddWhenNoConnections;
	}

	@Override
	public boolean isExitOnConnection() {
		return exitOnConnection;
	}

	@Override
	public boolean isConnect() {
		return connect;
	}

	@Override
	public String getUri() {
		return uri;
	}

	public boolean isNoUpdates() {
		return noUpdates;
	}

	@Override
	public boolean isNoResize() {
		return noResize;
	}

	public boolean isAlwaysOnTop() {
		return alwaysOnTop;
	}

	@Override
	public boolean isNoMove() {
		return noMove;
	}

	public String getSize() {
		return size;
	}

	@Override
	public boolean isNoClose() {
		return noClose;
	}

	public boolean isNoSidebar() {
		return noSidebar;
	}

	@Override
	public boolean isNoMinimize() {
		return noMinimize;
	}

	@Override
	public boolean isNoSystemTray() {
		return noSystemTray;
	}

	@Override
	public boolean isCreateIfDoesntExist() {
		return createIfDoesntExist;
	}

	public static Main getInstance() {
		return instance;
	}

	@Override
	public void restart() {
		exit();
		System.exit(99);
	}

	public void shutdown() {
		exit();
		System.exit(0);
	}

	@Override
	protected UpdateService createUpdateService() {
		if(isElevatableToAdministrator())
			return super.createUpdateService();
		else
			return new NoUpdateService(this);
	}

	boolean isElevatableToAdministrator() {
		if(OS.isWindows()) {
			for(var grp : Advapi32Util.getCurrentUserGroups()) {
				if(SID_ADMINISTRATORS_GROUP.equals(grp.sidString)) {
					return true;
				}
			}
			return false;
		}
		else if(OS.isLinux()) {
			try {
				return Arrays.asList(String.join(" ", runCommandAndCaptureOutput("id", "-Gn")).split("\\s+")).contains("sudo");
			} catch (IOException e) {
				getLog().error("Failed to test for user groups, assuming can elevate.", e);
			}
		}
		else if(OS.isMacOs()) {
			try {
				return Arrays.asList(String.join(" ", runCommandAndCaptureOutput("id", "-Gn")).split("\\s+")).contains("admin");
			} catch (IOException e) {
				getLog().error("Failed to test for user groups, assuming can elevate.", e);
			}
		}
		return true;
	}

	@Override
	protected boolean isInteractive() {
		return true;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		uiContext.ifPresentOrElse(c -> c.open(), () -> {
//			try {
//				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
//					| UnsupportedLookAndFeelException e) {
//			}
			System.exit(
					new CommandLine(new Main()).execute(args));
		});

	}

	@Override
	protected PromptingCertManager createCertManager() {
		return new ClientPromptingCertManager(Client.BUNDLE, this) {

			@Override
			public boolean isToolkitThread() {
				return Platform.isFxApplicationThread();
			}

			@Override
			public void runOnToolkitThread(Runnable r) {
				Platform.runLater(r);
			}

			@Override
			public boolean promptForCertificate(PromptType alertType, String title,
					String content, String key, String hostname, String message) {
				return uiContext.orElseThrow(()-> new IllegalStateException("UI Not registered.")).promptForCertificate(AlertType.valueOf(alertType.name()), title, content, key, hostname, message, this);
			}

		};
	}

	@Override
	public String getVersion() {
		return AppVersion.getVersion("com.logonbox/client-logonbox-vpn-gui-jfx");
	}

	@Override
	public boolean isConsole() {
		return false;
	}

	@Override
	public void startupPerformed(String parameters) {
		uiContext.orElseThrow(()-> new IllegalStateException("UI Not registered.")).open();
	}

	@SuppressWarnings("unchecked")
	public <M extends AppContext> M uiContext(UIContext<?> uiContext) {
		if(Main.uiContext.isPresent())
			throw new IllegalStateException("Already registered.");
		Main.uiContext = Optional.of(uiContext);
		return (M) this;
	}
	
    static Collection<String> runCommandAndCaptureOutput(String... args) throws IOException {
        var largs = new ArrayList<String>(Arrays.asList(args));
        var pb = new ProcessBuilder(largs);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        var lines = Utils.readLines(new InputStreamReader(p.getInputStream()));
        try {
            int ret = p.waitFor();
            if (ret != 0) {
                throw new IOException("Command '" + String.join(" ", largs) + "' returned non-zero status. Returned "
                        + ret + ". " + String.join("\n", lines));
            }
        } catch (InterruptedException e) {
            throw new IOException(e.getMessage(), e);
        }
        return lines;
    }
}
