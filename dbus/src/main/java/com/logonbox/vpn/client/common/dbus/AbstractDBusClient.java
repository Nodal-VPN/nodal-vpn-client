package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.AbstractClient;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.api.IVPNConnection;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.connections.IDisconnectCallback;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusException;
//import org.freedesktop.dbus.interfaces.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import picocli.CommandLine.Option;

public abstract class AbstractDBusClient extends AbstractClient implements DBusClient {

	public final static File CLIENT_HOME = new File(
			System.getProperty("user.home") + File.separator + ".logonbox-vpn-client");
	public final static File CLIENT_CONFIG_HOME = new File(CLIENT_HOME, "conf");

	public interface BusLifecycleListener {
		void busInitializer(AbstractConnection connection) throws DBusException;

		default void busGone() {
		}
	}

	final static int DEFAULT_TIMEOUT = 10000;
	static Logger log;

	private static final String BUS_NAME = "com.logonbox.vpn";
	private static final String ROOT_OBJECT_PATH = "/com/logonbox/vpn";

	@Option(names = { "-a", "--address-file" }, description = "File to obtain DBUS address from.")
	private String addressFile;
	
	@Option(names = { "-ba", "--bus-address" }, description = "Use an alternative bus address.")
	private String busAddress;
	
	@Option(names = { "-sb", "--session-bus" }, description = "Use session bus.")
	private boolean sessionBus;
	
	@Option(names = { "-u",
			"--as-user" }, description = "Act on behalf of another user, only an adminstrator can do this.")
	private String asUser;


    private AbstractConnection conn;
    private Object initLock = new Object();
	private ScheduledFuture<?> pingTask;
    private boolean supportsAuthorization;
    private Map<Long, List<AutoCloseable>> eventsMap = new HashMap<>();
    private boolean busAvailable;
    private List<AutoCloseable> handles;

	protected AbstractDBusClient() {
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

	@Override
    public boolean isBusAvailable() {
        return busAvailable;
    }

    public boolean isSupportsAuthorization() {
		return supportsAuthorization;
	}

	public void setSupportsAuthorization(boolean supportsAuthorization) {
		this.supportsAuthorization = supportsAuthorization;
	}

	public AbstractConnection getBus() {
		return conn;
	}

	@Override
    public VPNConnection getVPNConnection(long id) {
		lazyInit();
		try {
			return conn.getExportedObject(BUS_NAME, String.format("%s/%d", ROOT_OBJECT_PATH, id), VPNConnection.class);
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to get connection.");
		}
	}

	@Override
    public List<IVPNConnection> getVPNConnections() {
	    if(isBusAvailable())
    		return getVPNOrFail().getConnections().stream().map(p -> {
    		    /* TODO check this is needed still, why can't instances be directly used */
    			try {
    				return conn.getExportedObject(BUS_NAME, p.getPath(), VPNConnection.class);
    			} catch (DBusException e) {
    				throw new IllegalStateException(String.format("Failed to get connection %s.", p.getPath()));
    			}
    		}).collect(Collectors.toList());
	    else
	        return Collections.emptyList();
	}
	
	
	@Override
    public void checkVpnManagerAvailable() throws IllegalStateException {
	    try {
	        ((VPN)getVPNOrFail()).ping();
	    }
	    catch(Exception e) {
	        throw new IllegalStateException("Ping failed.", e);
	    }
    }

    protected void removeConnectionEvents(Long id)  {
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

    protected void listenConnectionEvents(IVPNConnection connection)  {        
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

    protected void disconnectFromBus() {
		conn.disconnect();
	}

	protected final void onInit() throws Exception {

		String fixedAddress = getServerDBusAddress(addressFile);
		if (conn == null || !conn.isConnected() || (StringUtils.isNotBlank(fixedAddress) && !fixedAddress.equals(conn.getAddress().toString()) )) {
			conn = createBusConnection();
			getLog().info("Got bus connection.");
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

	protected AbstractConnection createBusConnection() throws Exception {

		String fixedAddress = getServerDBusAddress(addressFile);
		String busAddress = this.busAddress;
		if (StringUtils.isNotBlank(busAddress)) {
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
			log = LoggerFactory.getLogger(AbstractDBusClient.class);
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
		getLog().info("Loading remote objects.");
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
		

        /* Listen for events on all existing connections */
		for (IVPNConnection vpnConnection : getVPNOrFail().getConnections()) {
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

	private void cancelPingTask() {
		if (pingTask != null) {
			getLog().info("Stopping pinging.");
			pingTask.cancel(false);
			pingTask = null;
		}
	}

	private void busGone() {
		synchronized (initLock) {
			cancelPingTask();

			if (busAvailable) {

				busAvailable = false;
				
				onVpnGone.forEach(Runnable::run);
				
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

}
