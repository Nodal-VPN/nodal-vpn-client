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
package com.logonbox.vpn.client.desktop;

import com.install4j.api.launcher.StartupNotification;
import com.install4j.api.launcher.StartupNotification.Listener;
import com.logonbox.vpn.client.app.SimpleLoggingConfig;
import com.logonbox.vpn.client.common.ClientPromptingCertManager;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.LoggingConfig.Audience;
import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.dbus.app.AbstractDBusApp;
import com.logonbox.vpn.client.dbus.client.DBusVpnManager;
import com.logonbox.vpn.client.gui.jfx.JfxAppContext;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.sshtools.jaul.ArtifactVersion;
import com.sshtools.jaul.NoUpdateService;
import com.sshtools.jaul.UpdateService;
import com.sshtools.liftlib.OS;

import org.slf4j.event.Level;

//import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

//import javax.swing.UIManager;
//import javax.swing.UnsupportedLookAndFeelException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.nativeimage.annotations.Reflectable;

@Command(name = "jad-vpn-gui", mixinStandardHelpOptions = true, description = "Start the VPN client graphical user interface.", versionProvider = DesktopVPN.VersionProvider.class
) 
public class DesktopVPN extends AbstractDBusApp implements Listener, JfxAppContext<VpnConnection> {
	@Reflectable
    public final static class VersionProvider implements IVersionProvider {
        
        public VersionProvider() {}
        
        @Override
        public String[] getVersion() throws Exception {
            return new String[] {
                "GUI: " + ArtifactVersion.getVersion("com.logonbox", "client-logonbox-vpn-desktop"),
                "DBus Java: " + ArtifactVersion.getVersion("com.github.hypfvieh", "dbus-java-core")
            };
        }
    }
	
	/**
	 * Used to get version from Maven meta-data
	 */
	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-gui-jfx";

	private static DesktopVPN instance;

	private static Optional<UIContext<?>> uiContext = Optional.empty();

	public static DesktopVPN getInstance() {
		return instance;
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
					new CommandLine(new DesktopVPN()).execute(args));
		});

	}

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

    @Option(names = { "--connect-uri" }, negatable = true, description = "By default, connections created by a supplied URI will be immediately activated. This prevents that.")
    private boolean connectUri = true;

	@Option(names = { "-n", "--create" }, description = "Create a new connection if one with the provided URI does not exist (requires URI parameter).")
	private boolean createIfDoesntExist;

    @Option(names = { "--options" }, description = "Show the options page on startup.")
    private boolean options;

	@Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
	private String uri;

	public DesktopVPN() {
		super();
		instance = this;

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

		StartupNotification.registerStartupListener(this);
	}

    @Override
	protected int onCall() throws Exception {

        log = initApp();

        log.info("Desktop App Version: {}", getVersion());
        log.info("DBus Version: {}", ArtifactVersion.getVersion("com.github.hypfvieh", "dbus-java-core"));
        log.info("OS: {}", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")");
        try {
            log.info("CWD: {}", new File(".").getCanonicalPath());
        } catch (IOException e) {
        }
        log.info("JVM Mode: {}", OS.isNativeImage() ? "Native" : "Interpreted");
        
        getVpnManager().start();
		Application.launch(DesktopVPNApp.class, new String[0]);
		return 0;
	}

	public String getSize() {
		return size;
	}

	@Override
    public boolean isOptions() {
        return options;
    }

    @Override
	public String getUri() {
		return uri;
	}

	public boolean isAlwaysOnTop() {
		return alwaysOnTop;
	}

	@Override
	public boolean isConnect() {
		return connect;
	}

	@Override
	public boolean isConsole() {
		return false;
	}

	@Override
	public boolean isCreateIfDoesntExist() {
		return createIfDoesntExist;
	}

	@Override
	public boolean isExitOnConnection() {
		return exitOnConnection;
	}

	@Override
	public boolean isInteractive() {
		return true;
	}

	@Override
	public boolean isNoAddWhenNoConnections() {
		return noAddWhenNoConnections;
	}

    @Override
    public boolean isConnectUri() {
        return connectUri;
    }

	@Override
	public boolean isNoClose() {
		return noClose;
	}

	@Override
	public boolean isNoMinimize() {
		return noMinimize;
	}

	@Override
	public boolean isNoMove() {
		return noMove;
	}

	@Override
	public boolean isNoResize() {
		return noResize;
	}
	
    @Override
	public boolean isNoSystemTray() {
		return noSystemTray;
	}

	public boolean isNoUpdates() {
		return noUpdates;
	}

	@Override
	public void startupPerformed(String parameters) {
		uiContext.orElseThrow(()-> new IllegalStateException("UI Not registered.")).open();
	}

	@SuppressWarnings("unchecked")
	public <M extends JfxAppContext<VpnConnection>> M uiContext(UIContext<VpnConnection> uiContext) {
		if(DesktopVPN.uiContext.isPresent())
			throw new IllegalStateException("Already registered.");
		DesktopVPN.uiContext = Optional.of(uiContext);
		return (M) this;
	}

	@Override
    protected DBusVpnManager.Builder buildVpnManager(DBusVpnManager.Builder builder) {
        return builder.withAuthorization();
    }

	@Override
	protected PromptingCertManager createCertManager() {
		return new ClientPromptingCertManager(DesktopVPNApp.RESOURCES, this) {

			@Override
			public boolean isToolkitThread() {
				return Platform.isFxApplicationThread();
			}

			@Override
			public boolean promptForCertificate(PromptType alertType, String title,
					String content, String key, String hostname, String message) {
				return uiContext.orElseThrow(()-> new IllegalStateException("UI Not registered.")).promptForCertificate(AlertType.valueOf(alertType.name()), title, content, key, hostname, message, this);
			}

			@Override
			public void runOnToolkitThread(Runnable r) {
				Platform.runLater(r);
			}

		};
	}

	@Override
    protected LoggingConfig createLoggingConfig() {
        return new SimpleLoggingConfig(Level.WARN, Map.of(
            Audience.USER, "default-log-gui.properties",
            Audience.CONSOLE, "default-log-gui-console.properties",
            Audience.DEVELOPER, "default-log-gui-developer.properties"
        ));
    }

	@Override
	protected UpdateService createUpdateService() {
		if(PlatformUtilities.get().isElevatableToAdministrator()) {
            getLog().info("Elevatable to administrator, so updates allowed.");
			return super.createUpdateService();
		}
		else {
            getLog().info("Not elevatable to administrator, so updates NOT allowed.");
			return new NoUpdateService(this);
		}
	}

    protected String getArtifactVersion() {
        return ArtifactVersion.getVersion("com.logonbox", "client-logonbox-vpn-desktop");
    }
}
