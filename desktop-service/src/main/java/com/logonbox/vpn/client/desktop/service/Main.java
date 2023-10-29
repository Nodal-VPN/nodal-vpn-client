package com.logonbox.vpn.client.desktop.service;

import com.logonbox.vpn.client.AbstractService;
import com.logonbox.vpn.client.app.SimpleLoggingConfig;
import com.logonbox.vpn.client.common.App;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.LoggingConfig.Audience;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.dbus.VPN;
import com.logonbox.vpn.client.common.dbus.VPNFrontEnd;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.desktop.service.dbus.VpnConnectionImpl;
import com.logonbox.vpn.client.desktop.service.dbus.VpnImpl;
import com.logonbox.vpn.client.service.ClientService.Listener;
import com.sshtools.liftlib.Helper;
import com.sshtools.liftlib.OS;

import org.freedesktop.dbus.bin.EmbeddedDBusDaemon;
import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.connections.BusAddress;
import org.freedesktop.dbus.connections.IDisconnectCallback;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.connections.transports.TransportBuilder;
import org.freedesktop.dbus.connections.transports.TransportBuilder.SaslAuthMode;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.Message;
import org.freedesktop.dbus.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Command(name = "lbvpn-service", mixinStandardHelpOptions = true, description = "Command line interface to the LogonBox VPN service.", versionProvider =  Main.VersionProvider.class)
@Bundle
@Resource({"default-log-service\\.properties", "default-log4j-service-console\\.properties"})
public class Main extends AbstractService<VpnConnection> implements Callable<Integer>, DesktopServiceContext, Listener {

	public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(Main.class.getName());
	
	@Reflectable
    public final static class VersionProvider implements IVersionProvider {
        
        public VersionProvider() {}
	    
        @Override
        public String[] getVersion() throws Exception {
            return new String[] {
                "Service: " + AppVersion.getVersion("com.logonbox", "client-logonbox-vpn-desktop-service"),
                "VPN Library: " + AppVersion.getVersion("com.logonbox", "logonbox-vpn-lib"),
                "DBus Java: " +AppVersion.getVersion("com.github.hypfvieh", "dbus-java-core")
            };
        }
    }
	
	final static int DEFAULT_TIMEOUT = 10000;

	static Logger log;
    static final long PING_TIMEOUT = TimeUnit.SECONDS.toMillis(45);

	public static void main(String[] args) throws Exception {
		Main cli = new Main();
		int ret = new CommandLine(cli).execute(args);
		if (ret > 0)
			System.exit(ret);

		/* If ret = 0, we just wait for threads to die naturally */
	}

	private EmbeddedDBusDaemon daemon;
	private AbstractConnection conn;
	private ScheduledFuture<?> connTask;
	private ScheduledFuture<?> checkTask;

    @Option(names = { "--elevate" }, hidden = true, paramLabel = "URI", description = "Elevated helper.")
    private Optional<String> elevate = Optional.empty();

	@Option(names = { "-ba", "--bus-address" }, description = "Address of Bus.")
	private String address;

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    private Optional<Level> level;

    @Option(names = { "-LA",
            "--log-audience" }, description = "Log audience.")
    private Optional<Audience> loggingAudience;

	@Option(names = { "-e",
			"--embedded-bus" }, description = "Force use of embedded DBus service. Usually it is enabled by default for anything other than Linux.")
	private boolean embeddedBus;

	@Option(names = { "-sb",
			"--session-bus" }, description = "Force use of session DBus service. Usually it is automatically detected.")
	private boolean sessionBus;

	@Option(names = { "-R",
			"--no-registration-required" }, description = "Enable this to allow unregistered DBus clients to control the VPN. Not recommended for security reasons, anyone would be able to control anyone else's VPN connections.")
	private boolean noRegistrationRequired;

	@Option(names = { "-t",
			"--tcp-bus" }, description = "Force use of TCP DBus service. Usually it is enabled by default for Windows only.")
	private boolean tcpBus;

	@Option(names = { "-ud",
			"--unix-domain-socket-bus" }, description = "Force use of UNIX DBus service. Usually it is enabled by default for operating systems other than Windows.")
	private boolean unixBus;

	@Option(names = { "-u", "--auth" }, description = "Mask of SASL authentication method to use.")
	private SaslAuthMode authMode = SaslAuthMode.AUTH_ANONYMOUS;
	
	/* TODO report this to Install4J. */
	@Option(names = {"start-launchd"}, hidden = true, description = "Work-around for apparent bug in Install4j for SystemD native launchers for *Linux*. This argument for Mac OS is added!")
	private boolean startLaunchD;

	private BusAddress embeddedBusAddress;
    private Map<String, VPNFrontEnd> frontEnds = Collections.synchronizedMap(new HashMap<>());

	public Main() throws Exception {
		super(BUNDLE);
	}

	public Level getDefaultLevel() {
		return Level.WARN;
	}

    @Override
    public boolean hasFrontEnd(String source) {
        return frontEnds.containsKey(source);
    }

    public VPNFrontEnd getFrontEnd(String source) {
        return frontEnds.get(source);
    }

    @Override
    public void deregisterFrontEnd(String source) {
        synchronized (frontEnds) {
            VPNFrontEnd fe = frontEnds.remove(source);
            if (fe == null) {
                throw new IllegalArgumentException(String.format("Front end '%s' not registered.", source));
            } else
                log.info(String.format("De-registered front-end %s.", source));
        }
    }

    @Override
    public VPNFrontEnd registerFrontEnd(String source) {
        synchronized (frontEnds) {
            VPNFrontEnd fe = frontEnds.get(source);
            if (fe != null) {
                throw new IllegalArgumentException(String.format("Front end '%s' already registered.", source));
            }
            fe = new VPNFrontEnd(source);
            frontEnds.put(source, fe);
            return fe;
        }
    }

    @Override
    public Collection<VPNFrontEnd> getFrontEnds() {
        return frontEnds.values();
    }

	@Override
	public final Integer call() throws Exception {
	    if(elevate.isPresent()) {
	        Helper.main(new String[] {elevate.get()});
	        return 0;
	    }

        getLogging().init(calcLoggingAudience(loggingAudience), level);
        
        /* Now we can start actually logging */
        log = LoggerFactory.getLogger(getClass());
		
		try {

			if (!buildServices()) {
				System.exit(3);
			}

			/*
			 * Have database, so enough to get configuration for log level
			 */
			Level cfgLevel = getConfigurationRepository().getValue(null, ConfigurationItem.LOG_LEVEL);
			if(level.isPresent()) {
                getLogging().setLevel(level.get());
			}
			else if (cfgLevel != null) {
			    getLogging().setLevel(cfgLevel);
			}
			
			/* About */
			log.info(String.format("VPN Desktop Service Version: %s",
					AppVersion.getVersion("com.logonbox", "client-logonbox-vpn-desktop-service")));
			log.info(String.format("VPN Library Version: %s",
                    AppVersion.getVersion("com.logonbox", "logonbox-vpn-lib")));
	        log.info(String.format("DBus Version: %s", AppVersion.getVersion("com.github.hypfvieh", "dbus-java-core")));
			log.info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch")
					+ " (" + System.getProperty("os.version") + ")"));

			if (!configureDBus()) {
				System.exit(3);
			}

			if (!startServices()) {
				System.exit(3);
			}

			if (!createVpn()) {
				System.exit(1);
			}
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					close();
				}
			});

			if (!startSavedConnections()) {
				log.warn("Not all connections started.");
			}

		} catch (Exception e) {
			log.error("Failed to start", e);
			try {
				Thread.sleep(2500L);
			} catch (InterruptedException e1) {
			}
			return 1;
		}

		return 0;
	}

    @Override
    protected LoggingConfig createLoggingConfig() {
        return new SimpleLoggingConfig(Level.WARN, Map.of(
                Audience.USER, "default-log-service.properties",
                Audience.CONSOLE, "default-log-service-console.properties",
                Audience.DEVELOPER, "default-log-service-developer.properties"
        ));
    }


    @Override
	protected void onShutdown(boolean restart) {
		System.exit(restart ? 99 : 0);
	}

	@Override
    public void fireExit() {
        try {
            sendMessage(new VPN.Exit("/com/logonbox/vpn"));
        } catch (DBusException e) {
        }
        
    }

    @Override
    protected void onConfigurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue) {
        if(getConnection() != null) {
            try {
                sendMessage(new VPN.GlobalConfigChange("/com/logonbox/vpn", item.getKey(), newValue == null ? "" : newValue.toString()));
            } catch (DBusException e) {
                throw new IllegalStateException("Failed to send event.", e);
            }
        }
        
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

    protected final Optional<IVpn<VpnConnection>> buildVpn() {

        try {
            /* DBus */
            if (log.isInfoEnabled()) {
                log.info(String.format("Exporting VPN services to DBus"));
            }
            var vpn = new VpnImpl(this);
            conn.exportObject("/com/logonbox/vpn", vpn);
            if (log.isInfoEnabled()) {
                log.info(String.format("    VPN"));
            }
            for (var connectionStatus : getClientService().getStatus(null)) {
                var connection = connectionStatus.getConnection();
                log.info(String.format("    Connection %d - %s", connection.getId(), connection.getDisplayName()));
                conn.exportObject(String.format("/com/logonbox/vpn/%d", connection.getId()),
                        new VpnConnectionImpl(this, connection));
            }

            return Optional.of(vpn);
        } catch (Exception e) {
            log.error("Failed to publish service", e);
            return Optional.empty();
        }
    }

    @Override
    public void sendMessage(Message message) {
        if(conn != null)
            conn.sendMessage(message);
    }

    @Override
    public void fireAuthorize(Connection connection, String authorizeUri) {

        try {
            sendMessage(
                    new VpnConnection.Authorize(String.format("/com/logonbox/vpn/%d", connection.getId()),
                            authorizeUri, connection.getMode().name()));
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to signal authorize.", e);
        }
    }

    @Override
    public void fireConnectionUpdating(Connection connection) {

        try {
            sendMessage(new VPN.ConnectionUpdating("/com/logonbox/vpn", connection.getId()));
        } catch (DBusException e) {
            log.error("Failed to signal connection updating.", e);
        }
        
    }

    @Override
    public void fireConnectionUpdated(Connection connection) {

        try {
            sendMessage(new VPN.ConnectionUpdated("/com/logonbox/vpn", connection.getId()));
        } catch (DBusException e) {
            log.error("Failed to signal connection updated.", e);
        }
        
    }

    @Override
    public void fireConnectionRemoving(Connection connection) {

        try {
            sendMessage(new VPN.ConnectionRemoving("/com/logonbox/vpn", connection.getId()));
        } catch (DBusException e) {
            log.error("Failed to signal connection removing.", e);
        }
        
    }

    @Override
    public void fireConnecting(Connection connection) {

        try {
            sendMessage(
                    new VpnConnection.Connecting(String.format("/com/logonbox/vpn/%d", connection.getId())));
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to send disconnected message.", e);
        }
        
    }

    @Override
    public void fireDisconnected(Connection connection, String reason) {

        try {
            if (log.isInfoEnabled()) {
                log.info("Sending disconnected event on bus");
            }

            var message = new VpnConnection.Disconnected(
                    String.format("/com/logonbox/vpn/%d", connection.getId()), reason == null ? "" : reason, connection.getDisplayName(), connection.getHostname());
            sendMessage(message);

            if (log.isInfoEnabled()) {
                log.info("Sent disconnected event on bus");
            }
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to send disconnected message.", e);
        }
        
    }

    @Override
    public void fireDisconnecting(Connection c, String reason) {
        try {
            if (log.isInfoEnabled()) {
                log.info("Sending disconnecting event");
            }
            sendMessage(new VpnConnection.Disconnecting(
                    String.format("/com/logonbox/vpn/%d", c.getId()), reason == null ? "" : reason));
        } catch (DBusException e) {
            log.error("Failed to send disconnected event.", e);
        }
        
    }

    @Override
    public AbstractConnection getConnection() {
        return conn;
    }

    @Override
    public void connectionRemoved(Connection connection) {
        try {
            getConnection().unExportObject(String.format("/com/logonbox/vpn/%d", connection.getId()));
            sendMessage(new VPN.ConnectionRemoved("/com/logonbox/vpn", connection.getId()));
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to send removed signal.", e);
        }
    }

    @Override
    public void connectionAdded(Connection connection) {

        try {
            getConnection().exportObject(String.format("/com/logonbox/vpn/%d", connection.getId()),
                    new VpnConnectionImpl(this, connection));
            sendMessage(new VPN.ConnectionAdded("/com/logonbox/vpn", connection.getId()));
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to send added signal.", e);
        }
        
    }

    @Override
    public void fireConnectionAdding() {
        try {
            sendMessage(new VPN.ConnectionAdding("/com/logonbox/vpn"));
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to send adding signal.", e);
        }
    }

    @Override
    public void fireTemporarilyOffline(Connection connection, Exception reason) {
        try {
            sendMessage(new VpnConnection.TemporarilyOffline(
                    String.format("/com/logonbox/vpn/%d", connection.getId()),
                    reason.getMessage() == null ? "" : reason.getMessage()));
        } catch (DBusException e) {
            log.error("Failed to send temporarily offline.", e);
        }
        
    }

    @Override
    public void fireFailed(Connection connection, String message, String cause, String trace) {
        try {
            sendMessage(new VpnConnection.Failed(String.format("/com/logonbox/vpn/%d", connection.getId()),
                    message, cause, trace));
        } catch (DBusException e) {
            log.error("Failed to send failed message.", e);
        }
    }

    @Override
    protected void onStartServices() {

        getQueue().scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            Set<VPNFrontEnd> toRemove = new LinkedHashSet<>();
            synchronized (getFrontEnds()) {
                for (VPNFrontEnd fe : getFrontEnds()) {
                    if (fe.getLastPing() < now - PING_TIMEOUT) {
                        toRemove.add(fe);
                    }
                }
            }
            for (VPNFrontEnd fe : toRemove) {
                log.warn(String.format("Front-end with source %s hasn't pinged for at least %dms", fe.getSource(),
                        PING_TIMEOUT));
                deregisterFrontEnd(fe.getSource());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void registered(VPNFrontEnd frontEnd) {

        log.info(String.format("Registered front-end %s as %s, %s, %s", frontEnd.getSource(), frontEnd.getUsername(),
                frontEnd.isSupportsAuthorization() ? "supports auth" : "doesnt support auth",
                frontEnd.isInteractive() ? "interactive" : "not interactive"));

    }

    @Override
    public void fireConnected(Connection connection) {

        try {
            sendMessage(new VpnConnection.Connected(
                    String.format("/com/logonbox/vpn/%d", connection.getId())));
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to send event.", e);
        }
        
    }

    @Override
    public void promptForCertificate(PromptType alertType, String title, String content, String key, String hostname,
            String message) {

        if (getFrontEnds().isEmpty())
            throw new IllegalStateException(
                    "Cannot prompt to accept invalid certificate. A front-end must be running.");
        
        try {
            sendMessage(new VPN.CertificatePrompt(VPN.OBJECT_PATH, alertType.name(), title, content, key, hostname, message));
        } catch (DBusException e) {
            throw new IllegalStateException(
                    "Cannot prompt to accept invalid certificate. Front-end running, but message could not be sent.",
                    e);
        }
        
    }

    private void shutdownEmbeddedDaemon() {
		if (checkTask != null) {
			checkTask.cancel(false);
			checkTask = null;
		}
		if (daemon != null) {
			try {
				log.info("Shutting down embedded dbus daemon.");
				daemon.close();
			} catch (IOException e) {
				log.error("Failed to shutdown DBus service.", e);
			} finally {
				daemon = null;
			}
		}
	}
    
    private boolean isSideBySideDbusDaemonAvailable() {
        if(Files.exists(Paths.get("pom.xml"))) {
            log.info("In developer mode (pom.xml exists), looking for side-by-side `dbus-daemon` running instance.");
            var dbusProps = getSideBySideDbusDaemonPropertiesPath();
            if(Files.exists(dbusProps)) {
                log.warn("Using developer dbus-daemon that exists side-by-side with this module. If this is not what you want, remove {}", dbusProps);
                return true;
            }
            else
                log.info("No side-by-side daemon, using default algorithm to determine bus address");
        }
        return false;
        
    }

    private Path getSideBySideDbusDaemonPropertiesPath() {
        return Paths.get("..", "dbus-daemon", "conf", "dbus.properties");
    }

	private boolean connect() throws DBusException, IOException {
		
		while(true) {
			if (connTask != null) {
				connTask.cancel(false);
				connTask = null;
			}
	
			String newAddress = address;
			if(!embeddedBus && isSideBySideDbusDaemonAvailable()) {
			    try(var in = Files.newBufferedReader(getSideBySideDbusDaemonPropertiesPath())) {
			        var p = new Properties();
			        p.load(in);
			        newAddress = p.getProperty("address");
			    }
                log.info(String.format("Connectin to DBus @%s", newAddress));
                conn = configureBuilder(DBusConnectionBuilder.forAddress(newAddress)).build();
                log.info(String.format("Ready on DBus @%s", newAddress));
			}
			else if (OS.isLinux() && !embeddedBus) {
				log.info("Will use built-in Linux DBus");
				if (newAddress != null) {
					log.info(String.format("Connectin to DBus @%s", newAddress));
					conn = configureBuilder(DBusConnectionBuilder.forAddress(newAddress)).build();
					log.info(String.format("Ready on DBus @%s", newAddress));
				} else if (OS.isAdministrator()) {
					if (sessionBus) {
						log.info("Per configuration, connecting to Session DBus");
						conn = configureBuilder(DBusConnectionBuilder.forSessionBus()).build();
						log.info("Ready on Session DBus");
						newAddress = conn.getAddress().toString();
					} else {
						log.info("Connecting to System DBus");
						conn = configureBuilder(DBusConnectionBuilder.forSystemBus()).build();
						log.info("Ready on System DBus");
						newAddress = conn.getAddress().toString();
					}
				} else {
					log.info("Not administrator, connecting to Session DBus");
					conn = configureBuilder(DBusConnectionBuilder.forSessionBus()).build();
					log.info("Ready on Session DBus");
					newAddress = conn.getAddress().toString();
				}
				embeddedBusAddress = BusAddress.of(newAddress);
				
			} else {
				log.info(String.format("Creating new DBus broker. Initial address is %s", Utils.isBlank(newAddress) ? "BLANK" : newAddress));
				if (newAddress == null) {
					if (!tcpBus || unixBus) {
						log.info("Using UNIX domain socket bus");
						newAddress = TransportBuilder.createDynamicSession("UNIX", true);
						if(newAddress == null)
						    throw new IllegalStateException("Did not get UNIX domain socket address for bus. Are the appropriate transport modules installed?");
						log.info(String.format("DBus-Java gave us %s", newAddress));
					} else {
						log.info("Using TCP bus");
						newAddress = TransportBuilder.createDynamicSession("TCP", true);
                        if(newAddress == null)
                            throw new IllegalStateException("Did not get TCP socket address for bus. Are the appropriate transport modules installed?");
						log.info(String.format("DBus-Java gave us %s", newAddress));
					}
				}
	
				embeddedBusAddress = BusAddress.of(newAddress);
				if (!embeddedBusAddress.hasParameter("guid")) {
					/* Add a GUID if user supplied bus address without one */
					newAddress += ",guid=" + Util.genGUID();
					embeddedBusAddress = BusAddress.of(newAddress);
				}
	
				if (embeddedBusAddress.isListeningSocket()) {
					/* Strip listen=true to get the address to uses as a client */
					newAddress = newAddress.replace(",listen=true", "");
					embeddedBusAddress = BusAddress.of(newAddress);
				}
				
				/* Work around for Windows. We need the domain socket file to be created
				 * somewhere that can be read by anyone. C:/Windows/TEMP cannot be 
				 * read by a "Normal User" without elevating.
				 */
				if (OS.isWindows() && embeddedBusAddress.getBusType().equals("UNIX")) {
					File publicDir = new File("C:\\Users\\Public");
					if (publicDir.exists()) {
						File vpnAppData = new File(publicDir, "AppData\\LogonBox\\VPN");
	
						if (!vpnAppData.exists() && !vpnAppData.mkdirs())
							throw new IOException("Failed to create public directory for domain socket file.");
						
						/* Clean up a bit so we don't get too many dead socket files. This
						 * will leave the 4 most recent.
						 */
						try {
							var l = new ArrayList<>(Arrays.asList(vpnAppData.listFiles((f) ->  f.getName().startsWith("dbus-"))));
							Collections.sort(l, (p1, p2) -> {
							    try {
    							    var f1 = Files.getLastModifiedTime(p1.toPath());
                                    var f2 = Files.getLastModifiedTime(p2.toPath());
                                    return f1.compareTo(f2);
							    }
							    catch(IOException e) {
							        return 0;
							    }
							});
							while(l.size() > 4) {
							    l.get(0).delete();
							}
						}
						catch(Exception e) {
							log.warn("Failed to remove old dbus files.", e);
						}
	
						newAddress = newAddress.replace("path=" + System.getProperty("java.io.tmpdir"),
								"path=" + vpnAppData.getAbsolutePath().replace('/', '\\') + "\\");
						log.info(String.format("Adjusting DBus path from %s to %s (%s)", embeddedBusAddress, newAddress, System.getProperty("java.io.tmpdir")));
						embeddedBusAddress = BusAddress.of(newAddress);
					}
				}
				else if (OS.isMacOs() && embeddedBusAddress.getBusType().equals("UNIX")) {
					/* Work around for Mac OS X. We need the domain socket file to be created
					 * somewhere that can be read by anyone. /var/folders/zz/XXXXXXXXXXXXXXXXXXXXxxx/T/dbus-XXXXXXXXX cannot be 
					 * read by a "Normal User" without elevating.
					 */
					File publicDir = new File("/tmp");
					if (publicDir.exists()) {
						File vpnAppData = new File(publicDir, "/logonbox-vpn-client");
						if (!vpnAppData.exists() && !vpnAppData.mkdirs())
							throw new IOException("Failed to create public directory for domain socket file.");
	
						newAddress = newAddress.replace("path=" + System.getProperty("java.io.tmpdir"),
								"path=" + vpnAppData.getAbsolutePath() + "/");
						log.info(String.format("Adjusting DBus path from %s to %s (%s)", embeddedBusAddress, newAddress, System.getProperty("java.io.tmpdir")));
						embeddedBusAddress = BusAddress.of(newAddress);
					}
				}
	
				boolean startedBus = false;
				if (daemon == null) {
					BusAddress listenBusAddress = BusAddress.of(newAddress);
					String listenAddress = newAddress;
					if (!listenBusAddress.isListeningSocket()) {
						listenAddress = newAddress + ",listen=true";
						listenBusAddress = BusAddress.of(listenAddress);
					}
	
					log.info(String.format("Starting embedded bus on address %s (auth types: %s)",
							listenBusAddress.toString(), authMode));
					daemon = new EmbeddedDBusDaemon(listenBusAddress);
					daemon.setSaslAuthMode(authMode);
					daemon.startInBackgroundAndWait();
					
					log.info(String.format("Started embedded bus on address %s", listenBusAddress));				
	
					log.info(String.format("Connecting to embedded DBus %s", embeddedBusAddress));
							conn = configureBuilder(DBusConnectionBuilder.forAddress(embeddedBusAddress)).build();
							log.info(String.format("Connected to embedded DBus %s", embeddedBusAddress));

					startedBus = true;
					
				}
				else {
					log.info(String.format("Connecting to embedded DBus %s", embeddedBusAddress));
					conn = configureBuilder(DBusConnectionBuilder.forAddress(embeddedBusAddress)).build();
				}
	
				/*
				 * Not ideal but we need read / write access to the domain socket from non-root
				 * users.
				 * 
				 * TODO secure this a bit. at least use a group permission
				 */
				if (startedBus) {
					if (embeddedBusAddress.getBusType().equals("UNIX")) {
						var busPath = Paths.get(embeddedBusAddress.getParameterValue("path"));
						getPlatformService().openToEveryone(busPath);
						log.info("Monitoring {}", busPath);
						checkTask = getQueue().scheduleAtFixedRate(() -> {
							if(!Files.exists(busPath)) {
								log.warn("DBus socket file {} has gone missing, restarting.", busPath);
								shutdownEmbeddedDaemon();
								disconnectAndRetry();
							}
						}, 10, 10, TimeUnit.SECONDS);
					}
				}
			}
			log.info(String.format("Requesting name from Bus %s", newAddress));
	
			
			conn.setDisconnectCallback(new IDisconnectCallback() {
			    public void disconnectOnError(IOException _ex) {
					disconnectAndRetry();
			    }
			});

			try {
				/* NOTE: Work around for Hello message not having been completely sent before trying to request name */
				while(true) {
					try {
						((DBusConnection)conn).requestBusName("com.logonbox.vpn");
						break;
					}
					catch(Exception dbe) {
//						if(!(dbe.getCause() instanceof AccessDenied))
//							throw dbe;
						Thread.sleep(500);
					}
				}
				
				// Now can tell the client
				if(embeddedBusAddress != null)
				    storeAddress(embeddedBusAddress.toString());
				
				return true;
			} catch (Exception e) {
				log.error("Failed to connect to DBus. No remote state monitoring or management.", e);
				return false;
			}
		}
	}

	private void storeAddress(String newAddress) throws IOException, FileNotFoundException {
		Properties properties = new Properties();
		properties.put("address", newAddress);
		File dbusPropertiesFile = getDBusPropertiesFile();
		try (FileOutputStream out = new FileOutputStream(dbusPropertiesFile)) {
			properties.store(out, "LogonBox VPN Client Service");
		}
	}
	
	private DBusConnectionBuilder configureBuilder(DBusConnectionBuilder builder) {
		builder.withShared(false);
		builder.transportConfig().withRegisterSelf(true);
		return builder;
	}

	private void disconnectAndRetry() {
		log.info("Disconnected from Bus, retrying");
		conn = null;
		connTask = getQueue().schedule(() -> {
			try {
				connect();
				createVpn();
			} catch (DBusException | IOException e) {
			}
		}, 10, TimeUnit.SECONDS);
	}

	private boolean configureDBus() throws Exception {
		return connect();
	}

	private File getDBusPropertiesFile() {
		return getPropertiesFile("dbus");
	}

	private File getPropertiesFile(String type) {
		File file;
		String path = System.getProperty("logonbox." + type);
		if (path != null) {
			file = new File(path);
		} else if (Boolean.getBoolean("logonbox.development")) {
			file = App.CLIENT_CONFIG_HOME.resolve(type + ".properties").toFile();
		} else {
			file = new File("conf" + File.separator + type + ".properties");
		}
		if (log.isInfoEnabled()) {
			log.info(String.format("Writing %s info to %s", type.toUpperCase(), file));
			String username = System.getProperty("user.name");
			log.info("Running as " + username);
		}

		file.getParentFile().mkdirs();
		return file;
	}

	@Override
	public boolean isRegistrationRequired() {
		return !noRegistrationRequired;
	}

	@Override
	protected final void onCleanUp() { 

        if(conn != null) {
            try {
                conn.close();
            }
            catch(Exception e) {
                log.warn("Failed to close bus connection.");
            }
        }
        
		var embedded = daemon != null;
		try {
			shutdownEmbeddedDaemon();
		}
		finally {
			if(embeddedBusAddress != null && embedded) {
				for(int i = 0 ; i < 10 ; i++) {
					try {
						String path = embeddedBusAddress.getParameterValue("path");
						if(path == null)
							break;
						Files.delete(Paths.get(path));
						break;
					} catch (IOException e) {
						log.warn("Failed to delete bus socket file, trying again.");
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e1) {
						}
					}
				}
			}
		}
	}
}
