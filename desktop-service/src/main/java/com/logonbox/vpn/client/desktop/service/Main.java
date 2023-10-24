package com.logonbox.vpn.client.desktop.service;

import com.logonbox.vpn.client.AbstractService;
import com.logonbox.vpn.client.common.AbstractClient;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVPN;
import com.logonbox.vpn.client.common.dbus.VPN;
import com.logonbox.vpn.client.common.dbus.VPNConnection;
import com.logonbox.vpn.client.common.dbus.VPNFrontEnd;
import com.logonbox.vpn.client.desktop.service.dbus.VPNConnectionImpl;
import com.logonbox.vpn.client.desktop.service.dbus.VPNImpl;
import com.logonbox.vpn.client.logging.SimpleLogger;
import com.logonbox.vpn.client.logging.SimpleLoggerConfiguration;
import com.logonbox.vpn.client.service.ClientService.Listener;
import com.sshtools.liftlib.OS;

import org.freedesktop.dbus.bin.EmbeddedDBusDaemon;
import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.connections.BusAddress;
import org.freedesktop.dbus.connections.IDisconnectCallback;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.connections.transports.TransportBuilder;
import org.freedesktop.dbus.connections.transports.TransportBuilder.SaslAuthMode;
import org.freedesktop.dbus.errors.AccessDenied;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.Message;
import org.freedesktop.dbus.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
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
import java.util.logging.Handler;
import java.util.logging.LogManager;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Command(name = "logonbox-vpn-service", mixinStandardHelpOptions = true, description = "Command line interface to the LogonBox VPN service.")
@Bundle
@Resource({"default-log-service\\.properties", "default-log4j-service-console\\.properties"})
public class Main extends AbstractService<VPNConnection> implements Callable<Integer>, DesktopServiceContext, Listener {

	public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(Main.class.getName());
	
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

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    private Optional<Level> level;

	@Option(names = { "-a", "--address" }, description = "Address of Bus.")
	private String address;

    @Option(names = { "-C",
            "--log-to-console" }, description = "Also direct all log output to console, unless logging configuration is overridden with `logonbox.vpn.logConfiguration` system property.")
    private boolean logToConsole;

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

	private BusAddress busAddress;
    private Map<String, VPNFrontEnd> frontEnds = Collections.synchronizedMap(new HashMap<>());

	public Main() throws Exception {
		super(BUNDLE);
	}

	@Override
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
	public Integer call() throws Exception {

		File logs = new File("logs");
		logs.mkdirs();

        if(logToConsole)
            System.setProperty(SimpleLoggerConfiguration.CONFIGURATION_FILE_KEY, "default-log-service-console.properties");
        else
            System.setProperty(SimpleLoggerConfiguration.CONFIGURATION_FILE_KEY, "default-log-service.properties");

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        log = LoggerFactory.getLogger(Main.class);

		try {

			if (!buildServices()) {
				System.exit(3);
			}

			/*
			 * Have database, so enough to get configuration for log level, we can start
			 * logging now
			 */
			Level cfgLevel = getConfigurationRepository().getValue(null, ConfigurationItem.LOG_LEVEL);
			if(level.isPresent()) {
                setLevel(level.get());
			}
			else if (cfgLevel != null) {
				setLevel(cfgLevel);
			}
			log.info(String.format("LogonBox VPN Client, version %s",
					AppVersion.getVersion(Main.ARTIFACT_COORDS)));
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
	public void setLevel(Level level) {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, level.toString());
        java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(toJulLevel(level));
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(toJulLevel(level));
        }
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

    protected final Optional<IVPN<VPNConnection>> buildVpn() {

        try {
            /* DBus */
            if (log.isInfoEnabled()) {
                log.info(String.format("Exporting VPN services to DBus"));
            }
            var vpn = new VPNImpl(this);
            conn.exportObject("/com/logonbox/vpn", vpn);
            if (log.isInfoEnabled()) {
                log.info(String.format("    VPN"));
            }
            for (var connectionStatus : getClientService().getStatus(null)) {
                var connection = connectionStatus.getConnection();
                log.info(String.format("    Connection %d - %s", connection.getId(), connection.getDisplayName()));
                conn.exportObject(String.format("/com/logonbox/vpn/%d", connection.getId()),
                        new VPNConnectionImpl(this, connection));
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
                    new VPNConnection.Authorize(String.format("/com/logonbox/vpn/%d", connection.getId()),
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
                    new VPNConnection.Connecting(String.format("/com/logonbox/vpn/%d", connection.getId())));
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

            var message = new VPNConnection.Disconnected(
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
            sendMessage(new VPNConnection.Disconnecting(
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
                    new VPNConnectionImpl(this, connection));
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
            sendMessage(new VPNConnection.TemporarilyOffline(
                    String.format("/com/logonbox/vpn/%d", connection.getId()),
                    reason.getMessage() == null ? "" : reason.getMessage()));
        } catch (DBusException e) {
            log.error("Failed to send temporarily offline.", e);
        }
        
    }

    @Override
    public void fireFailed(Connection connection, String message, String cause, String trace) {
        try {
            sendMessage(new VPNConnection.Failed(String.format("/com/logonbox/vpn/%d", connection.getId()),
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
            sendMessage(new VPNConnection.Connected(
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
            getConnection().sendMessage(new VPN.CertificatePrompt(((VPN)getVPN()).getObjectPath(),
                    alertType.name(), title, content, key, hostname, message));
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

	private boolean connect() throws DBusException, IOException {
		
		/*
		 * NOTE: This whole process takes too long when using EmbeddedDBusDaemon
		 * embedded DBUS daemon, due to an upstream bug.
		 * 
		 * There are work around's consisting of sleeps, polls and timeouts
		 * that ideally should not be here. 
		 */
		
		while(true) {
			if (connTask != null) {
				connTask.cancel(false);
				connTask = null;
			}
	
			String newAddress = address;
			if (OS.isLinux() && !embeddedBus) {
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
				busAddress = BusAddress.of(newAddress);
				
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
	
				busAddress = BusAddress.of(newAddress);
				if (!busAddress.hasParameter("guid")) {
					/* Add a GUID if user supplied bus address without one */
					newAddress += ",guid=" + Util.genGUID();
					busAddress = BusAddress.of(newAddress);
				}
	
				if (busAddress.isListeningSocket()) {
					/* Strip listen=true to get the address to uses as a client */
					newAddress = newAddress.replace(",listen=true", "");
					busAddress = BusAddress.of(newAddress);
				}
				
				/* Work around for Windows. We need the domain socket file to be created
				 * somewhere that can be read by anyone. C:/Windows/TEMP cannot be 
				 * read by a "Normal User" without elevating.
				 */
				if (OS.isWindows() && busAddress.getBusType().equals("UNIX")) {
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
						log.info(String.format("Adjusting DBus path from %s to %s (%s)", busAddress, newAddress, System.getProperty("java.io.tmpdir")));
						busAddress = BusAddress.of(newAddress);
					}
				}
				else if (OS.isMacOs() && busAddress.getBusType().equals("UNIX")) {
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
						log.info(String.format("Adjusting DBus path from %s to %s (%s)", busAddress, newAddress, System.getProperty("java.io.tmpdir")));
						busAddress = BusAddress.of(newAddress);
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
	
					log.info(String.format("Connecting to embedded DBus %s", busAddress));
							conn = configureBuilder(DBusConnectionBuilder.forAddress(busAddress)).build();
							log.info(String.format("Connected to embedded DBus %s", busAddress));

					startedBus = true;
					
				}
				else {
					log.info(String.format("Connecting to embedded DBus %s", busAddress));
					conn = configureBuilder(DBusConnectionBuilder.forAddress(busAddress)).build();
				}
	
				/*
				 * Not ideal but we need read / write access to the domain socket from non-root
				 * users.
				 * 
				 * TODO secure this a bit. at least use a group permission
				 */
				if (startedBus) {
					if (busAddress.getBusType().equals("UNIX")) {
						var busPath = Paths.get(busAddress.getParameterValue("path"));
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
	//		conn.addSigHandler(org.freedesktop.dbus.interfaces.Local.Disconnected.class,
	//				new DBusSigHandler<org.freedesktop.dbus.interfaces.Local.Disconnected>() {
	//
	//					@Override
	//					public void handle(org.freedesktop.dbus.interfaces.Local.Disconnected sig) {
	//						try {
	//							conn.removeSigHandler(org.freedesktop.dbus.interfaces.Local.Disconnected.class, this);
	//						} catch (DBusException e1) {
	//						}
	//						log.info("Disconnected from Bus, retrying");
	//						conn = null;
	//						connTask = queue.schedule(() -> {
	//							try {
	//								connect();
	//								publishDefaultServices();
	//							} catch (DBusException | IOException e) {
	//							}
	//						}, 10, TimeUnit.SECONDS);
	//
	//					}
	//				});
	
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
				storeAddress(busAddress.toString());
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
			file = new File(AbstractClient.CLIENT_CONFIG_HOME, type + ".properties");
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
			if(busAddress != null && embedded) {
				for(int i = 0 ; i < 10 ; i++) {
					try {
						String path = busAddress.getParameterValue("path");
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
	
	static java.util.logging.Level toJulLevel(Level defaultLevel) {
        switch(defaultLevel) {
        case TRACE:
            return java.util.logging.Level.FINEST;
        case DEBUG:
            return java.util.logging.Level.FINE;
        case INFO:
            return java.util.logging.Level.INFO;
        case WARN:
            return java.util.logging.Level.WARNING;
        case ERROR:
            return java.util.logging.Level.SEVERE;
        default:
            return java.util.logging.Level.OFF;
        }
    }
}
