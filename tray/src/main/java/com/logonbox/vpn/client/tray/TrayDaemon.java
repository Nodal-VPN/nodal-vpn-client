package com.logonbox.vpn.client.tray;


import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.dbus.AbstractDBusClient;
import com.logonbox.vpn.client.common.dbus.RemoteUI;
import com.logonbox.vpn.client.common.dbus.VPNConnection;
import com.logonbox.vpn.client.logging.SimpleLoggerConfiguration;
import com.sshtools.liftlib.OS;
import com.sshtools.twoslices.Slice;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;
import com.sshtools.twoslices.ToasterFactory;

import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Command(name = "logonbox-vpn-tray", mixinStandardHelpOptions = true, description = "Start the LogonBox VPN system tray.")
@Resource(siblings = true, value = { "default-log4j-tray\\.properties" })
@Reflectable
@TypeReflect(classes = true, fields = true, methods = true)
public class TrayDaemon extends AbstractDBusClient implements Callable<Integer> {

	/**
	 * Used to get version from Maven meta-data
	 */
	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-tray";

	private static TrayDaemon instance;
    private static Logger log;

	private Tray tray;

	private Map<Long, Slice> notificationsForConnections = new HashMap<>();

	public TrayDaemon() {
		instance = this;
		setSupportsAuthorization(false);
	}

	@Override
	public Integer call() throws Exception {
		getLog().info(String.format("LogonBox VPN Client Tray, version %s", AppVersion.getVersion(ARTIFACT_COORDS)));
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
		
		var handles = new ArrayList<AutoCloseable>();
		
		onVpnAvailable(() -> {
    		handles.addAll(Arrays.asList(
    		    onConfirmedExit(() -> shutdown()),
    	        onGlobalConfigChanged((item, val) -> tray.reload()),
    	        onAuthorize((connection, uri, mode) -> {
    	            requestAuthorize(connection);           
    	        }),
    	        onTemporarilyOffline((conx,reason) -> {
    	            clearNotificationForConnection(conx.getId());           
    	        }),
    	        onConnecting(conx -> {
    	            clearNotificationForConnection(conx.getId());
    	        }),
    	        onConnected(conx -> {
    	            notifyMessage(conx.getId(), MessageFormat.format(Tray.bundle.getString("connected"),
    	                    conx.getDisplayName(), conx.getHostname()), ToastType.INFO);
    	        }),
    	        onFailure((conx,reason,cause,trace) -> {
    	            notifyMessage(conx.getId(), reason, ToastType.ERROR);
    	        }),
    	        onDisconnected((conx, thisDisconnectionReason) -> {
    	            getLog().info("Disconnected " + conx.getId());
    	            try {
    	                /*
    	                 * WARN: Do not try to get a connection object directly here. The disconnection
    	                 * event may be the result of a deletion, so the connection won't exist any
    	                 * more. Use only what is available in the signal object. If you need more
    	                 * detail, add it to that.
    	                 */
    	                clearNotificationForConnection(conx.getId());
    	                if (Utils.isBlank(thisDisconnectionReason)
    	                        || Tray.bundle.getString("cancelled").equals(thisDisconnectionReason))
    	                    putNotificationForConnection(conx.getId(),
    	                            Toast.builder().title(Tray.bundle.getString("appName"))
    	                                    .content(MessageFormat.format(Tray.bundle.getString("disconnectedNoReason"),
    	                                            conx.getDisplayName(), conx.getHostname()))
    	                                    .type(ToastType.INFO).defaultAction(() -> open()).toast());
    	                else
    	                    putNotificationForConnection(conx.getId(),
    	                            Toast.builder().title(Tray.bundle.getString("appName"))
    	                                    .content(MessageFormat.format(Tray.bundle.getString("disconnected"),
    	                                            conx.getDisplayName(), conx.getHostname(), thisDisconnectionReason))
    	                                    .type(ToastType.INFO).defaultAction(() -> open())
    	                                    .action(Tray.bundle.getString("reconnect"), () -> {
    	                                        open();
    	                                        getScheduler().execute(() -> getVPNConnection(conx.getId()).connect());
    	                                    }).timeout(0).toast());
    	            } catch (Exception e) {
    	                getLog().error("Failed to get connection, delete not possible.", e);
    	            }
    	        })));
    		
            for (var c : getVPNConnections()) {
                if (c.getStatus().equals(Type.AUTHORIZING.name())) {
                    requestAuthorize(c);
                    break;
                }
            }
		});
		onVpnGone(() -> {
		    handles.forEach(h->{
                try {
                    h.close();
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to close signal handlere.");
                }
            });
		    handles.clear();
            tray.reload();
		});
		
		
		tray.loop();
		return 0;
	}

	public static TrayDaemon getInstance() {
		return instance;
	}

	@Override
	protected void beforeExit() {
		if (tray != null) {
			try {
				tray.close();
			} catch (Exception e) {
			}
		}
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

        System.setProperty(SimpleLoggerConfiguration.CONFIGURATION_FILE_KEY, "default-log-cli.properties");
        log = LoggerFactory.getLogger(TrayDaemon.class);
        
		var cli = new TrayDaemon();
		System.exit(new CommandLine(cli).execute(args));

	}

	@Override
	public String getVersion() {
		return AppVersion.getVersion("com.logonbox/client-logonbox-vpn-tray");
	}

	@Override
	public boolean isConsole() {
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
			@Override
            public void run() {
				try {
					if (OS.isWindows())
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
			int active = getVPNOrFail().getActiveButNonPersistentConnections();
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

	public void options() {
		try {
			getAltBus().getRemoteObject(RemoteUI.BUS_NAME, RemoteUI.OBJECT_PATH, RemoteUI.class).options();

		} catch (ServiceUnknown su) {
			startGui();
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to send remote call.");
		}
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
