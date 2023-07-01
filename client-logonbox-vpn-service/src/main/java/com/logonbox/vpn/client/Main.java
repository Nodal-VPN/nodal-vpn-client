package com.logonbox.vpn.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.CookieStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Level;
import org.apache.log4j.PropertyConfigurator;
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

import com.logonbox.vpn.client.dbus.VPNConnectionImpl;
import com.logonbox.vpn.client.dbus.VPNImpl;
import com.logonbox.vpn.client.service.ClientService;
import com.logonbox.vpn.client.service.ClientService.Listener;
import com.logonbox.vpn.client.service.ClientServiceImpl;
import com.logonbox.vpn.client.service.ConfigurationRepositoryImpl;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.client.wireguard.linux.LinuxPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.osx.BrewOSXPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.windows.WindowsPlatformServiceImpl;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.ConfigurationItem;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionRepository;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.CustomCookieStore;
import com.logonbox.vpn.common.client.HypersocketVersion;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;
import com.sshtools.forker.common.OS;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "logonbox-vpn-server", mixinStandardHelpOptions = true, description = "Command line interface to the LogonBox VPN service.")
public class Main implements Callable<Integer>, LocalContext, Listener {

	public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(Main.class.getName());
	
	final static int DEFAULT_TIMEOUT = 10000;

	static Logger log = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		Main cli = new Main();
		int ret = new CommandLine(cli).execute(args);
		if (ret > 0)
			System.exit(ret);

		/* If ret = 0, we just wait for threads to die naturally */
	}

	protected static Main instance;

	public static int randInt(int min, int max) {
		Random rand = new Random();
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}

	private ClientServiceImpl clientService;
	private PlatformService<?> platform;
	private DBusConnection conn;
	private Map<String, VPNFrontEnd> frontEnds = Collections.synchronizedMap(new HashMap<>());
	private EmbeddedDBusDaemon daemon;
	private Level defaultLogLevel;
	private ScheduledExecutorService queue = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> connTask;
	private ScheduledFuture<?> checkTask;

	@Option(names = { "-a", "--address" }, description = "Address of Bus.")
	private String address;

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

	private ConfigurationRepositoryImpl configurationRepository;
	private BusAddress busAddress;
	private ConnectionRepository connectionRepository;

	private PromptingCertManager promptingCertManager;

	private VPN vpn;

	private CookieStore cookieStore;

	public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-service";

	public Main() throws Exception {
		instance = this;
		if (SystemUtils.IS_OS_LINUX) {
			platform = new LinuxPlatformServiceImpl();
		} else if (SystemUtils.IS_OS_WINDOWS) {
			platform = new WindowsPlatformServiceImpl();
		} else if (SystemUtils.IS_OS_MAC_OSX) {
//			if(OsUtil.doesCommandExist("wg"))
			platform = new BrewOSXPlatformServiceImpl();
//			else
//				platform = new OSXPlatformServiceImpl();
		} else
			throw new UnsupportedOperationException(
					String.format("%s not currently supported.", System.getProperty("os.name")));
	}

	@Override
	public ScheduledExecutorService getQueue() {
		return queue;
	}

	@Override
	public CookieStore getCookieStore() {
		if (cookieStore == null) {
			cookieStore = new CustomCookieStore(new File(AbstractDBusClient.CLIENT_HOME, "service-cookies.dat"));
		}
		return cookieStore;
	}

	public static Main get() {
		return instance;
	}

	public Level getDefaultLogLevel() {
		return defaultLogLevel;
	}

	@Override
	public AbstractConnection getConnection() {
		return conn;
	}

	public ClientService getClientService() {
		return clientService;
	}

	@Override
	public PlatformService<?> getPlatformService() {
		return platform;
	}

	public PromptingCertManager getCertManager() {
		return promptingCertManager;
	}

	@Override
	public Integer call() throws Exception {

		File logs = new File("logs");
		logs.mkdirs();

		String logConfigPath = System.getProperty("hypersocket.logConfiguration", "");
		if (logConfigPath.equals("")) {
			/* Load default */
			PropertyConfigurator.configure(Main.class.getResource("/default-log4j-service.properties"));
		} else {
			File logConfigFile = new File(logConfigPath);
			if (logConfigFile.exists())
				PropertyConfigurator.configureAndWatch(logConfigPath);
			else
				PropertyConfigurator.configure(Main.class.getResource("/default-log4j-service.properties"));
		}

		try {

			if (!buildServices()) {
				System.exit(3);
			}

			/*
			 * Have database, so enough to get configuration for log level, we can start
			 * logging now
			 */
			Level cfgLevel = configurationRepository.getValue(null, ConfigurationItem.LOG_LEVEL);
			defaultLogLevel = org.apache.log4j.Logger.getRootLogger().getLevel();
			if (cfgLevel != null) {
				org.apache.log4j.Logger.getRootLogger().setLevel(cfgLevel);
			}
			log.info(String.format("LogonBox VPN Client, version %s",
					HypersocketVersion.getVersion(Main.ARTIFACT_COORDS)));
			log.info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch")
					+ " (" + System.getProperty("os.version") + ")"));

			if (!configureDBus()) {
				System.exit(3);
			}

			if (!startServices()) {
				System.exit(3);
			}

			if (!publishDefaultServices()) {
				System.exit(1);
			}
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					cleanUp();
				}
			});

			if (!clientService.startSavedConnections()) {
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
	public void shutdown(boolean restart) {
		System.exit(restart ? 99 : 0);
	}

	@Override
	public boolean hasFrontEnd(String source) {
		return frontEnds.containsKey(source);
	}

	@Override
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
	public void sendMessage(Message message) {
		if(conn != null)
			conn.sendMessage(message);
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
			if (SystemUtils.IS_OS_LINUX && !embeddedBus) {
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
				log.info(String.format("Creating new DBus broker. Initial address is %s", StringUtils.isBlank(newAddress) ? "BLANK" : newAddress));
				if (newAddress == null) {
					/*
					 * If no user supplied bus address, create one for an embedded daemon. All
					 * supported OS use domain sockets where possible except Windows
					 *
					 * TODO: switch to domain sockets all around with dbus-java 4.0.0+ :)
					 */
					if (!tcpBus || unixBus) {
						log.info("Using UNIX domain socket bus");
						newAddress = TransportBuilder.createDynamicSession("UNIX", true);
						log.info(String.format("DBus-Java gave us %s", newAddress));
					} else {
						log.info("Using TCP bus");
						newAddress = TransportBuilder.createDynamicSession("TCP", true);
						log.info(String.format("DBus-Java gave us %s", newAddress));
					}
				}
	
				busAddress = BusAddress.of(newAddress);
				if (!busAddress.getParameters().containsKey("guid")) {
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
				if (SystemUtils.IS_OS_WINDOWS && busAddress.getBusType().equals("UNIX")) {
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
				else if (SystemUtils.IS_OS_MAC_OSX && busAddress.getBusType().equals("UNIX")) {
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
					daemon.startInBackground();
					
					try {

						/* Argh! FIX this upstream. Without it, attempt to
						 * connect to embedded daemon very soon after it 
						 * starts up intermittently fails. The symptoms
						 * will be the JFX client continues to spin, waiting
						 * for the service to be fully exported
						 * 
						 *  See also below, this is wby this operating is in
						 *  a loop, we work around problems by just trying again. */
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
					} 
	
					log.info(String.format("Started embedded bus on address %s", listenBusAddress));				
	
					log.info(String.format("Connecting to embedded DBus %s", busAddress));
					long expire = TimeUnit.SECONDS.toMillis(15) + System.currentTimeMillis();
					while(System.currentTimeMillis() < expire) {
						try {
							conn = configureBuilder(DBusConnectionBuilder.forAddress(busAddress)).build();
							log.info(String.format("Connected to embedded DBus %s", busAddress));
							break;
						} catch (DBusException dbe) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
							}
						}
					}
					if(conn == null) {
						/* See above for reason for this loop */
						try {
							daemon.close();
							daemon = null;
						}
						catch(Exception e) {
						}
						log.warn("Activated work around, no local DBus connection yet. Trying again");
						continue;
					}
	
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
						FileSecurity.openToEveryone(busPath);
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
						conn.requestBusName("com.logonbox.vpn");
						break;
					}
					catch(DBusException dbe) {
						if(!(dbe.getCause() instanceof AccessDenied))
							throw dbe;
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
		builder.withRegisterSelf(true);
		return builder;
	}

	private void disconnectAndRetry() {
		log.info("Disconnected from Bus, retrying");
		conn = null;
		connTask = getQueue().schedule(() -> {
			try {
				connect();
				publishDefaultServices();
			} catch (DBusException | IOException e) {
			}
		}, 10, TimeUnit.SECONDS);
	}

	private boolean configureDBus() throws Exception {
		return connect();
	}

	private boolean startServices() {
		promptingCertManager = new ServicePromptingCertManager(BUNDLE, this);
		promptingCertManager.installCertificateVerifier();

		try {
			clientService.start();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start client configuration service.", e);
		}

		return true;
	}

	private boolean buildServices() throws Exception {
		/* TODO swap these and enable the dbconvert install action in Install4J project
		 * when ready to make ini files the default.
		 */
        Path dbDir = Paths.get("data");
		Path iniDir = Paths.get("conf").resolve("ini");
		if(Files.exists(iniDir) || !Files.exists(dbDir)) {
			log.info("Using file data backend");
			connectionRepository = new com.logonbox.vpn.client.ini.ConnectionRepositoryImpl();
		}
		else {
			log.warn("Using deprecated Derby data backend, please run `dbconvert` as this will soon be removed.");
			connectionRepository = new com.logonbox.vpn.client.db.ConnectionRepositoryImpl();
		}
		
		configurationRepository = new ConfigurationRepositoryImpl();
		clientService = new ClientServiceImpl(this, connectionRepository, configurationRepository);
		clientService.addListener(this);
		return true;

	}

	private File getDBusPropertiesFile() {
		return getPropertiesFile("dbus");
	}

	private File getPropertiesFile(String type) {
		File file;
		String path = System.getProperty("hypersocket." + type);

		if (log.isInfoEnabled()) {
			log.info(String.format("%s Path: %s", type.toUpperCase(), path));
		}
		if (path != null) {
			file = new File(path);
		} else if (Boolean.getBoolean("hypersocket.development")) {
			file = new File(AbstractDBusClient.CLIENT_CONFIG_HOME, type + ".properties");
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
	
	public VPN getVPN() {
		return vpn;
	}

	private boolean publishDefaultServices() {

		try {
			/* DBus */
			if (log.isInfoEnabled()) {
				log.info(String.format("Exporting VPN services to DBus"));
			}
			vpn = new VPNImpl(this);
			conn.exportObject("/com/logonbox/vpn", vpn);
			if (log.isInfoEnabled()) {
				log.info(String.format("    VPN"));
			}
			for (ConnectionStatus connectionStatus : clientService.getStatus(null)) {
				Connection connection = connectionStatus.getConnection();
				log.info(String.format("    Connection %d - %s", connection.getId(), connection.getDisplayName()));
				conn.exportObject(String.format("/com/logonbox/vpn/%d", connection.getId()),
						new VPNConnectionImpl(this, connection));
			}

			return true;
		} catch (Exception e) {
			log.error("Failed to publish service", e);
			return false;
		}
	}

	@Override
	public boolean isRegistrationRequired() {
		return !noRegistrationRequired;
	}

	@Override
	public Collection<VPNFrontEnd> getFrontEnds() {
		return frontEnds.values();
	}
	
	protected void cleanUp() {
		log.info("Shutdown clean up.");
		checkForUninstalling();
		clientService.stopService();
		try {
			getQueue().shutdown();
		}
		finally {
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
				try {
					connectionRepository.close();
				}
				catch(IOException ioe) {
					log.error("Failed to close connection repository.", ioe);
				}
			}
		}
	}

	protected void checkForUninstalling() {
		File flagFile = new File("uninstalling.txt");
		if(flagFile.exists()) {
			/* If uninstalling, stop all networks and tell all clients to exit
			 */
			try {
				sendMessage(new VPN.Exit("/com/logonbox/vpn"));
			} catch (DBusException e) {
			}
			try {
				for(Connection c : new ArrayList<>(clientService.getConnections(null))) {
					try {
						clientService.disconnect(c, "Shutdown");
					}
					catch(Exception e) {
					}
				}
				Thread.sleep(5000);
			}
			catch(Exception e) {
			}
		}
		flagFile.delete();
	}

	@Override
	public void configurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue) {

		if (item == ConfigurationItem.LOG_LEVEL) {
			String value = (String)newValue;
			try {
				if (StringUtils.isBlank(value))
					org.apache.log4j.Logger.getRootLogger().setLevel(getDefaultLogLevel());
				else
					org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.toLevel(value));
			}
			catch(Exception e) {
				if(log.isDebugEnabled())
					log.warn("Invalid log level, setting default. ", e);
				else
					log.warn("Invalid log level, setting default. " + e.getMessage());
				org.apache.log4j.Logger.getRootLogger().setLevel(getDefaultLogLevel());	
			}
		}
		
		if(getConnection() != null) {
			try {
				sendMessage(new VPN.GlobalConfigChange("/com/logonbox/vpn", item.getKey(), newValue == null ? "" : newValue.toString()));
			} catch (DBusException e) {
				throw new IllegalStateException("Failed to send event.", e);
			}
		}
		
	}

	@Override
	public SSLContext getSSLContext() {
		return promptingCertManager.getSSLContext();
	}

	protected boolean isStrictSSL() {
		return "true".equals(System.getProperty("logonbox.vpn.strictSSL", "true"));
	}

	@Override
	public SSLParameters getSSLParameters() {
		return promptingCertManager.getSSLParameters();
	}
}
