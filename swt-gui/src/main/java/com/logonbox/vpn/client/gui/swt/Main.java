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
package com.logonbox.vpn.client.gui.swt;

import com.logonbox.vpn.client.app.SimpleLoggingConfig;
import com.logonbox.vpn.client.common.ClientPromptingCertManager;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.LoggingConfig.Audience;
import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.dbus.app.AbstractDBusApp;
import com.logonbox.vpn.client.dbus.client.DBusVpnManager;
import com.sshtools.jaul.ArtifactVersion;
import com.sshtools.jaul.NoUpdateService;
import com.sshtools.jaul.UpdateService;
import com.sshtools.liftlib.OS;

import org.eclipse.swt.widgets.Display;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "jad-vpn-gui-swt", mixinStandardHelpOptions = true, description = "Start the VPN Client graphical user interface.")
public class Main extends AbstractDBusApp {

	/**
	 * Used to get version from Maven meta-data
	 */
	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-gui-jfx";

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

	@Option(names = { "-n",
			"--create" }, description = "Create a new connection if one with the provided URI does not exist (requires URI parameter).")
	private boolean createIfDoesntExist;

	@Option(names = { "-W", "--window-decorations" }, description = "Use standard window decorations.")
	private boolean windowDecorations;

	@Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
	private String uri;

	private Display display;
	private Client client;


    @Override
    protected LoggingConfig createLoggingConfig() {
        return new SimpleLoggingConfig(Level.WARN, Map.of(
            Audience.USER, "default-log-gui.properties",
            Audience.CONSOLE, "default-log-gui-console.properties",
            Audience.DEVELOPER, "default-log-gui-developer.properties"
        ));
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
        
		display = Display.getDefault();
		client = new Client(this, display);
		client.init();
		client.start();
		while (!client.getStage().isDisposed()) {
			try {
				if (!display.readAndDispatch())
					display.sleep();
			} catch (Throwable e) {
				if (!display.isDisposed()) {
					// SWTUtil.showError("SWT", e.getMessage());
				} else {
					break;
				}
			}
		}

		return 0;
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

	public boolean isWindowDecorations() {
		return windowDecorations;
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

	@Override
	public boolean isInteractive() {
		return true;
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
			public boolean isToolkitThread() {
				return Display.getCurrent() != null;
			}

			@Override
			public void runOnToolkitThread(Runnable r) {
				display.asyncExec(r);
			}
			
			@Override
			public boolean promptForCertificate(PromptType alertType, String title,
					String content, String key, String hostname, String message) {
				return client.promptForCertificate(alertType, title, content, key, hostname, message, this);
			}
		};
	}

    @Override
    protected UpdateService createUpdateService() {
        if(PlatformUtilities.get().isElevatableToAdministrator()) 
            return super.createUpdateService();
        else
            return new NoUpdateService(this);
    }
    
    @Override
    protected DBusVpnManager.Builder buildVpnManager(DBusVpnManager.Builder builder) {
        return builder.withAuthorization();
    }

    @Override
    protected String getArtifactVersion() {
        return ArtifactVersion.getVersion("com.logonbox", "client-logonbox-vpn-swt-gui");
    }

	@Override
	public boolean isConsole() {
		return false;
	}
}
