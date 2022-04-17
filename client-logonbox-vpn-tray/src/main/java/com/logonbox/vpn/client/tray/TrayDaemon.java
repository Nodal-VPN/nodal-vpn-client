package com.logonbox.vpn.client.tray;


import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;

import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.HypersocketVersion;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.dbus.RemoteUI;
import com.logonbox.vpn.common.client.dbus.RemoteUI.ConfirmedExit;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPN.Exit;
import com.logonbox.vpn.common.client.dbus.VPN.GlobalConfigChange;
import com.logonbox.vpn.common.client.dbus.VPNConnection;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Authorize;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Connected;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Connecting;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Disconnected;
import com.logonbox.vpn.common.client.dbus.VPNConnection.Failed;
import com.logonbox.vpn.common.client.dbus.VPNConnection.TemporarilyOffline;
import com.sshtools.twoslices.Slice;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;
import com.sshtools.twoslices.ToasterFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Command(name = "logonbox-vpn-tray", mixinStandardHelpOptions = true, description = "Start the LogonBox VPN system tray.")
@Resource(siblings = true, value = { "default-log4j-tray\\.properties" })
@Reflectable
@TypeReflect(classes = true, fields = true, methods = true)
public class TrayDaemon extends AbstractDBusClient implements Callable<Integer>, DBusSigHandler<ConfirmedExit> {

	/**
	 * Used to get version from Maven meta-data
	 */
	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-tray";

	private static TrayDaemon instance;

	private Tray tray;

	private DBusSigHandler<Disconnected> disconnectedHandler;
	private Map<Long, Slice> notificationsForConnections = new HashMap<>();
	private DBusSigHandler<Failed> failedHandler;
	private DBusSigHandler<Connected> connectedHandler;
	private DBusSigHandler<TemporarilyOffline> tempOfflineHandler;
	private DBusSigHandler<Connecting> connectingHandler;
	private DBusSigHandler<GlobalConfigChange> globalConfigChangeHandler;
	private DBusSigHandler<Authorize> authorizeHandler;
	private DBusSigHandler<Exit> exitHandler;

	public TrayDaemon() {
		super("%h/.logonbox-vpn-client/trap-app.log");
		instance = this;
		setSupportsAuthorization(false);
	}

	protected Integer onDbusCall() throws Exception {
		getLog().info(String.format("LogonBox VPN Client Tray, version %s", HypersocketVersion.getVersion(ARTIFACT_COORDS)));
		getLog().info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " ("
				+ System.getProperty("os.version") + ")"));
		try {
			getLog().info(String.format("CWD: %s", new File(".").getCanonicalPath()));
		} catch (IOException e) {
		}

		tray = new SWTTray(this);
		lazyInit();

		var settings = ToasterFactory.getSettings();
		settings.setAppName(Tray.bundle.getString("appName"));
		// TODO force usage of SWT for now to test it
//		if (SystemUtils.IS_OS_MAC_OSX) {
		settings.setPreferredToasterClassName("com.sshtools.twoslices.impl.SWTToaster");
//		}

		tray.loop();
		return 0;
	}

	public static TrayDaemon getInstance() {
		return instance;
	}

	@Override
	public void exit() {
		if (tray != null) {
			try {
				tray.close();
			} catch (Exception e) {
			}
		}
		super.exit();
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
		return false;
	}

	public static void main(String[] args) throws Exception {
		var cli = new TrayDaemon();
		System.exit(new CommandLine(cli).execute(args));

	}

	@Override
	protected String getVersion() {
		return HypersocketVersion.getVersion("com.logonbox/client-logonbox-vpn-tray");
	}

	@Override
	protected boolean isConsole() {
		return false;
	}

	@Override
	protected PromptingCertManager createCertManager() {
		return null;
	}

	public void open() {
		try {
			getBus().getRemoteObject(RemoteUI.BUS_NAME, RemoteUI.OBJECT_PATH, RemoteUI.class).open();
		} catch (ServiceUnknown su) {
			startGui();
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to send remote call.");
		}
	}

	public void notifyMessage(Long id, String msg, ToastType toastType) {
		clearNotificationForConnection(id);
		putNotificationForConnection(id, Toast.toast(toastType, Tray.bundle.getString("appName"), msg));
	}

	protected void startGui() {
		new Thread() {
			public void run() {
				try {
					if (SystemUtils.IS_OS_WINDOWS)
						Runtime.getRuntime()
								.exec(new File(System.getProperty("user.dir") + File.separator + "logonbox-vpn-gui.exe")
										.getAbsolutePath());
					else
						Runtime.getRuntime()
								.exec(new File(System.getProperty("user.dir") + File.separator + "logonbox-vpn-gui")
										.getAbsolutePath());
				} catch (IOException ioe) {
					throw new IllegalStateException("Failed to send remote command, and failed to start GUI.", ioe);
				}
			}
		}.start();
	}

	public void confirmExit() {
		try {
			int active = getVPN().getActiveButNonPersistentConnections();
			getLog().info("{} non-persistent connections", active);
			if (active == 0)
				throw new Exception("No active connections, just exit.");
			getBus().getRemoteObject(RemoteUI.BUS_NAME, RemoteUI.OBJECT_PATH, RemoteUI.class).confirmExit();
		} catch (Exception e) {
			try {
				getBus().getRemoteObject(RemoteUI.BUS_NAME, RemoteUI.OBJECT_PATH, RemoteUI.class).exitApp();
			} catch (Exception e2) {
			}
			shutdown();
		}
	}

	@Override
	protected void onBusGone() {
		try {
			var bus = getBus();
			bus.removeSigHandler(VPNConnection.Authorize.class, authorizeHandler);
			bus.removeSigHandler(VPN.GlobalConfigChange.class, globalConfigChangeHandler);
			bus.removeSigHandler(VPN.Exit.class, exitHandler);
			bus.removeSigHandler(VPNConnection.Disconnected.class, disconnectedHandler);
			bus.removeSigHandler(VPNConnection.Failed.class, failedHandler);
			bus.removeSigHandler(VPNConnection.Connected.class, connectedHandler);
			bus.removeSigHandler(VPNConnection.Connecting.class, connectingHandler);
			bus.removeSigHandler(VPNConnection.TemporarilyOffline.class, tempOfflineHandler);
			getAltBus().removeSigHandler(RemoteUI.ConfirmedExit.class, this);
		} catch (DBusException e) {
		} finally {
			tray.reload();
		}
	}

	@Override
	protected void onLoadRemote() {
		try {
			exitHandler = new DBusSigHandler<VPN.Exit>() {
				@Override
				public void handle(Exit sig) {
					shutdown();
				}
			};
			globalConfigChangeHandler = new DBusSigHandler<VPN.GlobalConfigChange>() {
				@Override
				public void handle(GlobalConfigChange sig) {
					tray.reload();
				}
			};
			authorizeHandler = new DBusSigHandler<VPNConnection.Authorize>() {
				@Override
				public void handle(Authorize sig) {
					VPNConnection connection = getVPNConnection(sig.getId());
					requestAuthorize(connection);
				}
			};
			tempOfflineHandler = new DBusSigHandler<VPNConnection.TemporarilyOffline>() {
				@Override
				public void handle(TemporarilyOffline sig) {
					clearNotificationForConnection(sig.getId());
				}
			};
			connectingHandler = new DBusSigHandler<VPNConnection.Connecting>() {
				@Override
				public void handle(Connecting sig) {
					clearNotificationForConnection(sig.getId());
				}
			};
			connectedHandler = new DBusSigHandler<VPNConnection.Connected>() {
				@Override
				public void handle(Connected sig) {
					VPNConnection connection = getVPNConnection(sig.getId());
					notifyMessage(sig.getId(), MessageFormat.format(Tray.bundle.getString("connected"),
							connection.getDisplayName(), connection.getHostname()), ToastType.INFO);
				}
			};
			failedHandler = new DBusSigHandler<VPNConnection.Failed>() {

				@Override
				public void handle(Failed sig) {
					String s = sig.getReason();
					notifyMessage(sig.getId(), s, ToastType.ERROR);
				}
			};
			disconnectedHandler = new DBusSigHandler<VPNConnection.Disconnected>() {
				@Override
				public void handle(Disconnected sig) {
					String thisDisconnectionReason = sig.getReason();
					getLog().info("Disconnected " + sig.getId());
					try {
						/*
						 * WARN: Do not try to get a connection object directly here. The disconnection
						 * event may be the result of a deletion, so the connection won't exist any
						 * more. Use only what is available in the signal object. If you need more
						 * detail, add it to that.
						 */
						clearNotificationForConnection(sig.getId());
						if (StringUtils.isBlank(thisDisconnectionReason)
								|| Tray.bundle.getString("cancelled").equals(thisDisconnectionReason))
							putNotificationForConnection(sig.getId(),
									Toast.builder().title(Tray.bundle.getString("appName"))
											.content(MessageFormat.format(Tray.bundle.getString("disconnectedNoReason"),
													sig.getDisplayName(), sig.getHostname()))
											.type(ToastType.INFO).defaultAction(() -> open()).toast());
						else
							putNotificationForConnection(sig.getId(),
									Toast.builder().title(Tray.bundle.getString("appName"))
											.content(MessageFormat.format(Tray.bundle.getString("disconnected"),
													sig.getDisplayName(), sig.getHostname(), thisDisconnectionReason))
											.type(ToastType.INFO).defaultAction(() -> open())
											.action(Tray.bundle.getString("reconnect"), () -> {
												open();
												getScheduler().execute(() -> getVPNConnection(sig.getId()).connect());
											}).timeout(0).toast());
					} catch (Exception e) {
						getLog().error("Failed to get connection, delete not possible.", e);
					}
				}
			};
			var bus = getBus();
			bus.addSigHandler(VPN.Exit.class, exitHandler);
			bus.addSigHandler(VPN.GlobalConfigChange.class, globalConfigChangeHandler);
			bus.addSigHandler(VPNConnection.Authorize.class, authorizeHandler);
			bus.addSigHandler(VPNConnection.Disconnected.class, disconnectedHandler);
			bus.addSigHandler(VPNConnection.Failed.class, failedHandler);
			bus.addSigHandler(VPNConnection.Connected.class, connectedHandler);
			bus.addSigHandler(VPNConnection.Connecting.class, connectingHandler);
			bus.addSigHandler(VPNConnection.TemporarilyOffline.class, tempOfflineHandler);

			for (VPNConnection c : getVPNConnections()) {
				if (c.getStatus().equals(Type.AUTHORIZING.name())) {
					requestAuthorize(c);
					break;
				}
			}

			/* Only active if GUI is */
			getAltBus().addSigHandler(RemoteUI.ConfirmedExit.class, RemoteUI.OBJECT_PATH, this);

		} catch (DBusException e) {
		}
	}

	public void options() {
		Toast.toast(ToastType.INFO, "Hello", "Some content");
		try {
			getAltBus().getRemoteObject(RemoteUI.BUS_NAME, RemoteUI.OBJECT_PATH, RemoteUI.class).options();
			;
		} catch (ServiceUnknown su) {
			startGui();
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to send remote call.");
		}
	}

	@Override
	public void handle(ConfirmedExit _signal) {
		shutdown();
	}

	private void putNotificationForConnection(Long id, Slice slice) {
		if (id != null) {
			notificationsForConnections.put(id, slice);
		}
	}

	private void requestAuthorize(VPNConnection connection) {
		putNotificationForConnection(connection.getId(),
				Toast.builder().title(Tray.bundle.getString("appName"))
						.content(MessageFormat.format(Tray.bundle.getString("authorize"), connection.getDisplayName(),
								connection.getHostname()))
						.type(ToastType.INFO).defaultAction(() -> open()).action(Tray.bundle.getString("open"), () -> {
							open();
							getScheduler().execute(() -> getVPNConnection(connection.getId()).connect());
						}).timeout(0).toast());
	}

	private void clearNotificationForConnection(Long id) {
		if (id != null) {
			Slice slice = notificationsForConnections.remove(id);
			if (slice != null) {
				try {
					slice.close();
				} catch (Exception e) {
				}
			}
		}
	}
}
