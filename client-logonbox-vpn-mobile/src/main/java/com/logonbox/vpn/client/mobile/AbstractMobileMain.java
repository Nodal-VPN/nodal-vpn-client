package com.logonbox.vpn.client.mobile;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.logonbox.vpn.client.gui.jfx.AppContext;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.ClientPromptingCertManager;
import com.logonbox.vpn.common.client.HypersocketVersion;
import com.logonbox.vpn.common.client.PromptingCertManager;

import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class AbstractMobileMain extends AbstractDBusClient implements Callable<Integer>, AppContext {
	static Logger log;

	public final static String SID_ADMINISTRATORS_GROUP = "S-1-5-32-544";

	/**
	 * Used to get version from Maven meta-data
	 */
	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-gui-jfx";

	private static AbstractMobileMain instance;

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

	private Level defaultLogLevel = Level.INFO;

	protected static Optional<UIContext> uiContext = Optional.empty();

	public AbstractMobileMain() {
		super();
		instance = this;
		setSupportsAuthorization(true);
		log = LoggerFactory.getLogger(AbstractMobileMain.class);

		log.info(String.format("LogonBox VPN Client GUI, version %s", HypersocketVersion.getVersion(ARTIFACT_COORDS)));
		log.info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")"));
		try {
			log.info(String.format("CWD: %s", new File(".").getCanonicalPath()));
		} catch (IOException e) {
		}
	}

	@Override
	public Integer call() throws Exception {
		Client.main(new String[0]);
		return 0;
	}

	@Override
	public Level getDefaultLogLevel() {
		return defaultLogLevel;
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

	public static AbstractMobileMain getInstance() {
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
	protected boolean isInteractive() {
		return true;
	}

	@Override
	protected PromptingCertManager createCertManager() {
		return new ClientPromptingCertManager(Client.BUNDLE, this) {

			@Override
			protected boolean isToolkitThread() {
				return Platform.isFxApplicationThread();
			}

			@Override
			protected void runOnToolkitThread(Runnable r) {
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
	protected String getVersion() {
		return HypersocketVersion.getVersion("com.logonbox/client-logonbox-vpn-mobile");
	}

	@Override
	protected boolean isConsole() {
		return false;
	}

	@SuppressWarnings("unchecked")
	public <M extends AppContext> M uiContext(UIContext uiContext) {
		if(AbstractMobileMain.uiContext.isPresent())
			throw new IllegalStateException("Already registered.");
		AbstractMobileMain.uiContext = Optional.of(uiContext);
		return (M) this;
	}

	@Override
	public void setLevel(Level valueOf) {
	}
}
