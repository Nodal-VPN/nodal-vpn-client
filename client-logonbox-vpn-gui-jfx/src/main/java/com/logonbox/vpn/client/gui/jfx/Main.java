package com.logonbox.vpn.client.gui.jfx;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.freedesktop.dbus.exceptions.DBusException;

import com.logonbox.vpn.common.client.AbstractUpdateableDBusClient;
import com.logonbox.vpn.common.client.ClientPromptingCertManager;
import com.logonbox.vpn.common.client.HypersocketVersion;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.dbus.RemoteUI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


@Command(name = "logonbox-vpn-gui", mixinStandardHelpOptions = true, description = "Start the LogonBox VPN graphical user interface.")
public class Main extends AbstractUpdateableDBusClient implements Callable<Integer> {

	/**
	 * Used to get version from Maven meta-data
	 */
	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-gui-jfx";

	private static Main instance;

	@Option(names = { "-C", "--no-close" }, description = "Do not allow the window to be closed manually.")
	private boolean noClose;

	@Option(names = { "-x", "--exit-on-connection" }, description = "Exit on successful connection.")
	private boolean exitOnConnection;

	@Option(names = { "-A",
			"--no-add-when-no-connections" }, description = "Do not show the Add connection page when no connections are configured.")
	private boolean noAddWhenNoConnections;

	@Option(names = { "-M", "--no-minimize" }, description = "Do not allow the window to be minimized manually.")
	private boolean noMinimize;

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

	@Option(names = { "-W", "--window-decorations" }, description = "Use standard window decorations.")
	private boolean windowDecorations;

	@Option(names = { "-n",
			"--create" }, description = "Create a new connection if one with the provided URI does not exist (requires URI parameter).")
	private boolean createIfDoesntExist;

	@Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
	private String uri;

	public Main() {
		super("%h/.logonbox-vpn-client/gui-app.log");
		instance = this;
		setSupportsAuthorization(true);
	}

	protected Integer onDbusCall() throws Exception {
		getLog().info(String.format("LogonBox VPN Client GUI, version %s", HypersocketVersion.getVersion(ARTIFACT_COORDS)));
		getLog().info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " ("
				+ System.getProperty("os.version") + ")"));
		try {
			getLog().info(String.format("CWD: %s", new File(".").getCanonicalPath()));
		} catch (IOException e) {
		}
		Application.launch(Client.class, new String[0]);
		return 0;
	}

	public boolean isWindowDecorations() {
		return windowDecorations;
	}

	public boolean isNoAddWhenNoConnections() {
		return noAddWhenNoConnections;
	}

	public boolean isExitOnConnection() {
		return exitOnConnection;
	}

	public boolean isConnect() {
		return connect;
	}

	public String getUri() {
		return uri;
	}

	public boolean isNoUpdates() {
		return noUpdates;
	}

	public boolean isNoResize() {
		return noResize;
	}

	public boolean isAlwaysOnTop() {
		return alwaysOnTop;
	}

	public boolean isNoMove() {
		return noMove;
	}

	public String getSize() {
		return size;
	}

	public boolean isNoClose() {
		return noClose;
	}

	public boolean isNoMinimize() {
		return noMinimize;
	}

	public boolean isCreateIfDoesntExist() {
		return createIfDoesntExist;
	}

	public static Main getInstance() {
		return instance;
	}

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
	protected void onLoadRemote() {
		try {
			getLog().info("Exporting remote interface.");
			var conn = getAltBus();
			conn.requestBusName(RemoteUI.BUS_NAME);
			conn.exportObject(RemoteUI.OBJECT_PATH, Client.get());
			getLog().info("Remote interface exported.");
		} catch (DBusException e) {
			getLog().error(
					"Failed to export remote. Other clients such as the tray icon will not be able to control this client.",
					e);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		System.exit(new CommandLine(new Main()).execute(args));
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
				return Client.get().promptForCertificate(AlertType.valueOf(alertType.name()), title, content, key, hostname, message, this);
			}

		};
	}

	@Override
	protected String getVersion() {
		return HypersocketVersion.getVersion("com.logonbox/client-logonbox-vpn-gui-jfx");
	}

	@Override
	protected boolean isConsole() {
		return false;
	}
}
