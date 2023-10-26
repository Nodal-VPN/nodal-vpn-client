package com.logonbox.vpn.client.dbus.app;

import com.logonbox.vpn.client.app.AbstractApp;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.dbus.RemoteUI;
import com.logonbox.vpn.client.common.dbus.RemoteUI.ConfirmedExit;
import com.logonbox.vpn.client.common.dbus.VPN;
import com.logonbox.vpn.client.common.dbus.VPNConnection;

import org.freedesktop.dbus.connections.IDisconnectCallback;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusException;
//import org.freedesktop.dbus.interfaces.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import picocli.CommandLine.Option;

public abstract class AbstractDBusApp extends AbstractApp<VPNConnection> {

	final static int DEFAULT_TIMEOUT = 10000;
	private static Logger log;

	private static final String BUS_NAME = "com.logonbox.vpn";
	private static final String ROOT_OBJECT_PATH = "/com/logonbox/vpn";

	@Option(names = { "-a", "--address-file" }, description = "File to obtain DBUS address from.")
	private String addressFile;
	
	@Option(names = { "-ba", "--bus-address" }, description = "Use an alternative bus address.")
	private String busAddress;
	
	@Option(names = { "-sb", "--session-bus" }, description = "Use session bus.")
	private boolean sessionBus;
	
    private DBusConnection conn;
    private Object initLock = new Object();
	private ScheduledFuture<?> pingTask;
    private boolean supportsAuthorization;
    private Map<Long, List<AutoCloseable>> eventsMap = new HashMap<>();
    private boolean busAvailable;
    private List<AutoCloseable> handles = Collections.emptyList();
    private List<AutoCloseable> altHandles = Collections.emptyList();
    private DBusConnection altConn;

	protected AbstractDBusApp() {
	    super();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (isBusAvailable()) {
                    try {
                        ((VPN)getVPNOrFail()).deregister();
                    } catch (Exception e) {
                        getLog().warn("De-registrating failed. Maybe service is already gone.");
                    }
                }
            }
        });
	}

    public DBusConnection getAltBus() {
        synchronized (initLock) {
            lazyInit();
            if (conn != null && conn.isConnected() && altConn == null) {
                if (conn.getAddress().getBusType().equals(DBusConnection.DBusBusType.SYSTEM.name())) {
                    try {
                        altConn = conn = DBusConnectionBuilder.forSessionBus().withShared(false).build();
                    } catch (DBusException e) {
                        throw new IllegalStateException("Could not get alternate bus.");
                    }
                } else {
                    altConn = conn;
                }
            }
            return altConn;
        }
    }

	@Override
    public void confirmExit() {
	    try {
            getBus().sendMessage(new ConfirmedExit(RemoteUI.OBJECT_PATH));
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to confirm exit.");
        }
    }

    @Override
    public final boolean isBusAvailable() {
        return busAvailable;
    }

    public final boolean isSupportsAuthorization() {
		return supportsAuthorization;
	}

	public final void setSupportsAuthorization(boolean supportsAuthorization) {
		this.supportsAuthorization = supportsAuthorization;
	}

	public final DBusConnection getBus() {
		return conn;
	}

	@Override
    public final VPNConnection getVPNConnection(long id) {
		lazyInit();
		try {
			return conn.getExportedObject(BUS_NAME, String.format("%s/%d", ROOT_OBJECT_PATH, id), VPNConnection.class);
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to get connection.");
		}
	}

	@Override
    public final List<VPNConnection> getVPNConnections() {
	    if(isBusAvailable())
    		return getVPNOrFail().getConnections().stream().map(p -> {
    		    /* TODO check this is needed still, why can't instances be directly used */
    			try {
    				return conn.getExportedObject(BUS_NAME, p.getObjectPath(), VPNConnection.class);
    			} catch (DBusException e) {
    				throw new IllegalStateException(String.format("Failed to get connection %s.", p.getPath()));
    			}
    		}).collect(Collectors.toList());
	    else
	        return Collections.emptyList();
	}
	
	
	@Override
    public final void checkVpnManagerAvailable() throws IllegalStateException {
	    try {
	        ((VPN)getVPNOrFail()).ping();
	    }
	    catch(Exception e) {
	        throw new IllegalStateException("Ping failed.", e);
	    }
    }

    protected final void removeConnectionEvents(Long id)  {
        var map = eventsMap.remove(id);
        if (map != null) {
            map.forEach(m -> {
                try {
                    m.close();
                }
                catch(Exception ioe) {
                }
            });
        }
    }

    protected final void listenConnectionEvents(VPNConnection connection)  {        
        var id = connection.getId();
        try {
            eventsMap.put(id, Arrays.asList(
                conn.addSigHandler(VPNConnection.Authorize.class, (VPNConnection)connection, (sig) -> {
                    onAuthorize.forEach(a -> a.authorize(connection, sig.getUri(), Mode.valueOf(sig.getMode())));
                }),
                conn.addSigHandler(VPNConnection.Connecting.class, (VPNConnection)connection, (sig) -> {
                    onConnecting.forEach(a -> a.accept(connection));
                }),
                conn.addSigHandler(VPNConnection.Connected.class, (VPNConnection)connection, (sig) -> {
                    onConnected.forEach(a -> a.accept(connection));
                }),
                conn.addSigHandler(VPNConnection.Disconnecting.class, (VPNConnection)connection, (sig) -> {
                    onDisconnecting.forEach(a -> a.accept(connection, sig.getReason()));
                }),
                conn.addSigHandler(VPNConnection.Disconnected.class, (VPNConnection)connection, (sig) -> {
                    onDisconnected.forEach(a -> a.accept(connection, sig.getReason()));
                }),
                conn.addSigHandler(VPNConnection.TemporarilyOffline.class, (VPNConnection)connection, (sig) -> {
                    onTemporarilyOffline.forEach(a -> a.accept(connection, sig.getReason()));
                }),
                conn.addSigHandler(VPNConnection.Failed.class, (VPNConnection)connection, (sig) -> {
                    onFailure.forEach(a -> a.failure(connection, sig.getReason(), sig.getCause(), sig.getTrace()));
                })
            ));
        }
        catch(DBusException dbe) {
            throw new IllegalStateException("Failed to configure signals.", dbe);
        }
    }

    protected final void disconnectFromBus() {
		conn.disconnect();
	}

	protected final void onInit() throws Exception {

		String fixedAddress = getServerDBusAddress(addressFile);
		if (conn == null || !conn.isConnected() || (Utils.isNotBlank(fixedAddress) && !fixedAddress.equals(conn.getAddress().toString()) )) {
			conn = createBusConnection();
			 getLog().debug("Got bus connection.");
			busAvailable = true;
		}

		conn.setDisconnectCallback(new IDisconnectCallback() {
			@Override
            public void disconnectOnError(IOException _ex) {
				busGone();
			}
		});

		/* Load the VPN object */
		loadRemote();
	}

	protected final DBusConnection createBusConnection() throws Exception {

		String fixedAddress = getServerDBusAddress(addressFile);
		String busAddress = this.busAddress;
		if (Utils.isNotBlank(busAddress)) {
			if (getLog().isDebugEnabled())
				getLog().debug("Getting bus. " + this.busAddress);
			return configureBuilder(DBusConnectionBuilder.forAddress(busAddress)).build();
		} else {
			if (sessionBus) {
				if (getLog().isDebugEnabled())
					getLog().debug("Getting session bus.");
				return configureBuilder(DBusConnectionBuilder.forSessionBus()).build();
			} else {
				if (fixedAddress == null) {
					if (getLog().isDebugEnabled())
						getLog().debug("Getting system bus.");
					return configureBuilder(DBusConnectionBuilder.forSystemBus()).build();
				} else {
					if (getLog().isDebugEnabled())
						getLog().debug("Getting fixed bus " + fixedAddress);
					return configureBuilder(DBusConnectionBuilder.forAddress(fixedAddress)).build();
				}
			}
		}
	}

	protected DBusConnectionBuilder configureBuilder(DBusConnectionBuilder builder) {
		builder.withShared(false);
		builder.transportConfig().withRegisterSelf(true);
		return builder;
	}

	protected abstract boolean isInteractive();

	protected Logger getLog() {
		if (log == null) {
			log = LoggerFactory.getLogger(AbstractDBusApp.class);
		}
		return log;
	}

	protected void onLazyInit() throws Exception {
		getLog().info("Trying connect to DBus");
		try {
			init();
		} catch (UnknownObject | DBusException | ServiceUnknown dbe) {
			busGone();
		} 
	}

	private void loadRemote() throws DBusException {
		getLog().debug("Loading remote objects.");
		VPN newVpn = conn.getExportedObject(BUS_NAME, ROOT_OBJECT_PATH, VPN.class);
		getLog().info("Got remote object, registering with DBus.");
		getLog().info("Registering with VPN service.");
		newVpn.register(getEffectiveUser(), isInteractive(), supportsAuthorization);
		getLog().info("Registered with VPN service.");
		setVPN(newVpn);
		
		handles = Arrays.asList(
		    conn.addSigHandler(VPN.ConnectionAdding.class, newVpn, 
	                    sig -> onConnectionAdding.forEach(Runnable::run)),
	        conn.addSigHandler(VPN.ConnectionAdded.class, 
	                newVpn, sig -> { 
	                    var conx = newVpn.getConnection(sig.getId());
	                    onConnectionAdded.forEach(r -> r.accept(conx));
	                    listenConnectionEvents(conx);
	                }),
            conn.addSigHandler(VPN.Exit.class, newVpn, 
                    sig -> exit()),
            conn.addSigHandler(VPN.CertificatePrompt.class, newVpn, 
                    this::handleCertPromptRequest),
            conn.addSigHandler(VPN.ConnectionRemoving.class, newVpn, 
                    sig ->  { 
                        var conx = newVpn.getConnection(sig.getId());
                        onConnectionRemoving.forEach(r -> r.accept(conx)); 
                    }),
            conn.addSigHandler(VPN.ConnectionRemoved.class, newVpn, 
                    sig -> { 
                        onConnectionRemoved.forEach(r -> r.accept(sig.getId())); 
                        removeConnectionEvents(sig.getId());
                    }),
            conn.addSigHandler(VPN.ConnectionUpdating.class, newVpn, 
                    sig -> {
                        var conx = newVpn.getConnection(sig.getId());
                        onConnectionUpdating.forEach(r -> r.accept(conx)); 
                    }),
            conn.addSigHandler(VPN.ConnectionUpdated.class, newVpn, 
                    sig -> { 
                        var conx = newVpn.getConnection(sig.getId());
                        onConnectionUpdated.forEach(r -> r.accept(conx)); 
                    }),
            conn.addSigHandler(VPN.GlobalConfigChange.class, newVpn, 
                    sig -> {
                        var item = ConfigurationItem.get(sig.getName());
                        onGlobalConfigChanged.forEach(r -> r.accept(item, sig.getValue())); 
                    })
		);


        /* Only active if GUI is */
		var altBus = getAltBus();
		try {
            altHandles = Arrays.asList(
                altBus.addSigHandler(RemoteUI.ConfirmedExit.class, RemoteUI.OBJECT_PATH, sig -> {
                    onConfirmedExit.forEach(r -> r.run());
                })
            );
		}
		catch(DBusException dbe) {
		    altHandles = Collections.emptyList();
		}

        /* Listen for events on all existing connections */
		for (var vpnConnection : getVPNOrFail().getConnections()) {
		      listenConnectionEvents(vpnConnection);
		}

        onVpnAvailable.forEach(Runnable::run);
		
		pingTask = getScheduler().scheduleAtFixedRate(() -> {
			synchronized (initLock) {
				if (isBusAvailable()) {
					try {
						((VPN)getVPNOrFail()).ping();
					} catch (Exception e) {
						busGone();
					}
				}
			}
		}, 5, 5, TimeUnit.SECONDS);
	}

	protected void handleCertPromptRequest(VPN.CertificatePrompt sig) {
		var cm = getCertManager();
		if(cm.isToolkitThread()) {
			if(cm.promptForCertificate(PromptType.valueOf(sig.getAlertType()), sig.getTitle(), sig.getContent(), sig.getKey(), sig.getHostname(), sig.getMessage())) {
				cm.accept(sig.getKey());
			}
			else {
				cm.reject(sig.getKey());
			}
		}
		else
			cm.runOnToolkitThread(() -> handleCertPromptRequest(sig));
	}

	private final void cancelPingTask() {
		if (pingTask != null) {
			getLog().info("Stopping pinging.");
			pingTask.cancel(false);
			pingTask = null;
		}
	}

	private final void busGone() {
		synchronized (initLock) {
			cancelPingTask();

			if (busAvailable) {

				busAvailable = false;
				
				onVpnGone.forEach(Runnable::run);

                if (altConn != null && !altConn.equals(conn)) {
                    altHandles.forEach(h -> {
                        try {
                            h.close();
                        } catch (Exception e) {
                        }
                    });
                    try {
                        altConn.disconnect();
                    }
                    catch(Exception e) {
                    }
                    finally {
                        altConn = null;
                    }
                }
				if (conn != null) {
	                handles.forEach(h -> {
	                    try {
	                        h.close();
	                    } catch (Exception e) {
	                    }
	                });
					try {
						conn.disconnect();
					}
					catch(Exception e) {
					}
					finally {
						conn = null;
					}
				}
				setVPN(null);
				getUpdateService().checkIfBusAvailable();
			}

			/*
			 * Only really likely to happen with the embedded bus. As the service itself
			 * hosts it.
			 */
			getScheduler().schedule(() -> {
				synchronized (initLock) {
					try {
						init();
					} catch (DBusException | ServiceUnknown | UnknownObject dbe) {
						if (getLog().isDebugEnabled())
							getLog().debug("Init() failed, retrying");
						busGone();
					} catch (RuntimeException re) {
						re.printStackTrace();
						throw re;
					} catch (Exception e) {
						e.printStackTrace();
						throw new IllegalStateException("Failed to schedule new connection.", e);
					}
				}
			}, 5, TimeUnit.SECONDS);
		}
	}

	protected abstract PromptingCertManager createCertManager();
	
	static String getServerDBusAddress(String addressFile) {
        Properties properties = new Properties();
        Path file;
        if(addressFile != null)
            file = Paths.get(addressFile);
        else if (System.getProperty("lbvpn.dbus") != null) {
            file = Paths.get(System.getProperty("lbvpn.dbus"));
        } else if (Files.exists(Paths.get("pom.xml"))) {
            /* Decide whether to look for side-by-side `dbus-daemon`, or if the
             * service is running with an embedded bus (legacy mode)
             */
            var dbusDaemonProps = Paths.get("..", "dbus-daemon", "conf", "dbus.properties");
            if(Files.exists(dbusDaemonProps))
                file = dbusDaemonProps;
            else
                file = Paths.get("..", "service", "conf", "dbus.properties");
        } else {
            file = Paths.get("conf", "dbus.properties");
        }
        if (Files.exists(file)) {
            try (var in = Files.newInputStream(file)) {
                properties.load(in);
                String addr = properties.getProperty("address", "");
                if (addr.equals(""))
                    throw new IllegalStateException("DBus address file exists, but has no content.");
                return addr;
            } catch (IOException ioe) {
                throw new IllegalStateException("Failed to read DBus address file.", ioe);
            }
        } else
            return null;
    }
}
