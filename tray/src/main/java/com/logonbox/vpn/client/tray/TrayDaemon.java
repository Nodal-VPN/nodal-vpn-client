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
package com.logonbox.vpn.client.tray;


import com.logonbox.vpn.client.app.SimpleLoggingConfig;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.LoggingConfig.Audience;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.TrayMode;
import com.logonbox.vpn.client.common.UiConfiguration;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IRemoteUI;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.dbus.app.AbstractDBusApp;
import com.sshtools.jaul.ArtifactVersion;
import com.sshtools.liftlib.OS;
import com.sshtools.twoslices.Slice;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;
import com.sshtools.twoslices.ToasterFactory;

import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Command(name = "jad-vpn-tray", mixinStandardHelpOptions = true, description = "Start the VPN Client system tray.", versionProvider = TrayDaemon.VersionProvider.class)
@Resource(siblings = true, value = { "default-log4j-tray\\.properties" })
@Reflectable
@TypeReflect(classes = true, fields = true, methods = true)
public class TrayDaemon extends AbstractDBusApp implements Callable<Integer> {
    @Reflectable
    public final static class VersionProvider implements IVersionProvider {
        
        public VersionProvider() {}
        @Override
        public String[] getVersion() throws Exception {
            return new String[] {
                "Tray: " + AppVersion.getVersion(TrayDaemon.class, "com.logonbox", "client-logonbox-vpn-tray"),
                "DBus Java: " + ArtifactVersion.getVersion("com.github.hypfvieh", "dbus-java-core")
            };
        }
    }
    
	private static TrayDaemon instance;

	public static TrayDaemon getInstance() {
		return instance;
	}

	public static void main(String[] args) throws Exception {
		var cli = new TrayDaemon();
		System.exit(new CommandLine(cli).execute(args));
	}

	private Tray tray;

	private Map<Long, Slice> notificationsForConnections = new HashMap<>();
    
    public TrayDaemon() {
		instance = this;
	}

	@Override
	protected int onCall() throws Exception {
	    
	    Utils.applicationLock("jad-vpn-tray");

        initApp();
        
		log.info("System Tray Version: {}", getVersion());
        log.info("DBus Version: {}", ArtifactVersion.getVersion("com.github.hypfvieh", "dbus-java-core"));
		log.info("OS: {}", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " ("
				+ System.getProperty("os.version") + ")");
		try {
			log.info("CWD: {}", new File(".").getCanonicalPath());
		} catch (IOException e) {
		}
        log.info("JVM Mode: {}", OS.isNativeImage() ? "Native" : "Interpreted");

		tray = new SWTTray(this);

		var settings = ToasterFactory.getSettings();
		settings.setAppName(Tray.bundle.getString("appName"));
		if(!OS.isLinux())
		    settings.setPreferredToasterClassName("com.sshtools.twoslices.impl.SWTToaster");
		
		var handles = new ArrayList<AutoCloseable>();
		
		var mgr = getVpnManager();
        mgr.onVpnAvailable(() -> {
    		handles.addAll(Arrays.asList(
    		    mgr.onConfirmedExit(() -> shutdown(false)),
    		    mgr.onGlobalConfigChanged((item, val) -> {
    		        if(item.equals(UiConfiguration.TRAY_MODE) && val.equals(TrayMode.OFF.name())) {
    		            System.exit(0);
    		        }
    		        else {
    		            tray.reload();
    		        }
    		    }),
    		    mgr.onAuthorize((connection, uri, mode,legacy) -> {
    	            requestAuthorize(connection);           
    	        }),
    		    mgr.onTemporarilyOffline((conx,reason) -> {
    	            clearNotificationForConnection(conx.getId());           
    	        }),
    		    mgr.onConnecting(conx -> {
    	            clearNotificationForConnection(conx.getId());
    	        }),
    		    mgr.onConnected(conx -> {
    	            notifyMessage(conx.getId(), MessageFormat.format(Tray.bundle.getString("connected"),
    	                    conx.getDisplayName(), conx.getHostname()), ToastType.INFO);
    	        }),
    		    mgr.onFailure((conx,reason,cause,trace) -> {
    	            notifyMessage(conx.getId(), reason, ToastType.ERROR);
    	        }),
    		    mgr.onDisconnected((conx, thisDisconnectionReason) -> {
    	            log.info("Disconnected " + conx.getId());
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
    	                                        getScheduler().execute(() -> mgr.getVpnOrFail().getConnection(conx.getId()).connect());
    	                                    }).timeout(0).toast());
    	            } catch (Exception e) {
    	                log.error("Failed to get connection, delete not possible.", e);
    	            }
    	        })));
    		
            for (var c : mgr.getVpnOrFail().getConnections()) {
                if (c.getStatus().equals(Type.AUTHORIZING.name())) {
                    requestAuthorize(c);
                    break;
                }
            }
		});
		getVpnManager().onVpnGone(() -> {
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
		getVpnManager().start();
		
		tray.loop();
		return 0;
	}

	public void confirmExit() {
		try {
			int active = getVpnManager().getVpnOrFail().getActiveButNonPersistentConnections();
			log.info("{} non-persistent connections", active);
			if (active == 0)
				throw new Exception("No active connections, just exit.");
			getVpnManager().getUserInterface().ifPresent(IRemoteUI::confirmExit);
		} catch (Exception e) {
			try {
	            getVpnManager().getUserInterface().ifPresent(IRemoteUI::exitApp);
			} catch (Exception e2) {
			}
			shutdown(false);
		}
	}

    @Override
    protected void afterExit() {
        System.exit(0);
    }

	@Override
	public boolean isConsole() {
		return false;
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

	public void notifyMessage(Long id, String msg, ToastType toastType) {
		clearNotificationForConnection(id);
		putNotificationForConnection(id, Toast.toast(toastType, Tray.bundle.getString("appName"), msg));
	}

	public void open() {
	    getVpnManager().getUserInterface().ifPresentOrElse(IRemoteUI::open, () -> startGui(false));
	}

	public void options() {
        getVpnManager().getUserInterface().ifPresentOrElse(IRemoteUI::options, () -> startGui(true));
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

	@Override
	protected PromptingCertManager createCertManager() {
		return null;
	}

	@Override
    protected LoggingConfig createLoggingConfig() {
        return new SimpleLoggingConfig(Level.INFO, Map.of(
                Audience.USER, "default-log-tray.properties",
                Audience.CONSOLE, "default-log-tray-console.properties",
                Audience.DEVELOPER, "default-log-tray-developer.properties"
        ));
    }

	protected void startGui(boolean options) {
		new Thread() {
			@Override
            public void run() {
				try {
				    getLog().info("Starting GUI");
				    
				    var cmd = Utils.findCommandPath("jad-vpn-gui");
				    var bldr = options ?  new ProcessBuilder(cmd, "--options") : new ProcessBuilder(cmd);
				    
					bldr.redirectError(Redirect.INHERIT);
                    bldr.redirectOutput(Redirect.INHERIT);
                    bldr.start();
				} catch (IOException ioe) {
					throw new IllegalStateException("Failed to send remote command, and failed to start GUI.", ioe);
				}
			}
		}.start();
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

    private void putNotificationForConnection(Long id, Slice slice) {
		if (id != null) {
			notificationsForConnections.put(id, slice);
		}
	}

    private void requestAuthorize(VpnConnection connection) {
        putNotificationForConnection(connection.getId(),
                Toast.builder().title(Tray.bundle.getString("appName"))
                        .content(MessageFormat.format(Tray.bundle.getString("authorize"), connection.getDisplayName(),
                                connection.getHostname()))
                        .type(ToastType.INFO).defaultAction(() -> open()).action(Tray.bundle.getString("open"), () -> {
                            open();
                            getScheduler().execute(() -> getVpnManager().getVpnOrFail().getConnection(connection.getId()).connect());
                        }).timeout(0).toast());
    }
}
