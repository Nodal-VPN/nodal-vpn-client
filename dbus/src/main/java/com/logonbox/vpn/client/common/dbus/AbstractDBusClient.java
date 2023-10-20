package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.AbstractClient;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.api.IVPN;
import com.logonbox.vpn.client.common.api.IVPNConnection;
import com.logonbox.vpn.client.common.dbus.VPN.CertificatePrompt;
import com.logonbox.vpn.drivers.lib.Vpn;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.connections.IDisconnectCallback;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.messages.DBusSignal;
//import org.freedesktop.dbus.interfaces.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public abstract class AbstractDBusClient extends AbstractClient {

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
    private List<BusLifecycleListener> busLifecycleListeners = new ArrayList<>();
	private ScheduledFuture<?> pingTask;
    private boolean supportsAuthorization;
	private DBusSigHandler<CertificatePrompt> certPromptHandler;
    private Map<Long, Map<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>>> eventsMap = new HashMap<>();

	protected AbstractDBusClient() {
	    super();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (isBusAvailable()) {
                    try {
                        ((VPN)getVPN()).deregister();
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

	public void addBusLifecycleListener(BusLifecycleListener busInitializer) throws DBusException {
		this.busLifecycleListeners.add(busInitializer);
		if (isBusAvailable()) {
			busInitializer.busInitializer(conn);
		}
	}

	public void removeBusLifecycleListener(BusLifecycleListener busInitializer) {
		this.busLifecycleListeners.remove(busInitializer);
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

    @Override
    public void busInitializer(AbstractConnection connection) {

        try {
            connection.addSigHandler(IVPN.ConnectionAdded.class, context.getManager().getVPN(),
                    new DBusSigHandler<IVPN.ConnectionAdded>() {
                        @Override
                        public void handle(IVPN.ConnectionAdded sig) {
                            maybeRunLater(() -> {
                                try {
                                    IVPNConnection addedConnection = context.getManager().getVPNConnection(sig.getId());
                                    try {
                                        listenConnectionEvents(connection, addedConnection);
                                    } catch (DBusException e) {
                                        throw new IllegalStateException("Failed to listen for new connections events.");
                                    }
                                } finally {
                                    LOG.info("Connection added");
                                }
                            });
                        }
                    });
            connection.addSigHandler(IVPN.ConnectionRemoved.class, context.getManager().getVPN(),
                    new DBusSigHandler<IVPN.ConnectionRemoved>() {
                        @Override
                        public void handle(IVPN.ConnectionRemoved sig) {
                            try {
                                removeConnectionEvents(connection, sig.getId());
                            } catch (DBusException e) {
                            }
                        }
                    });
            connection.addSigHandler(IVPN.Exit.class, context.getManager().getVPN(), new DBusSigHandler<IVPN.Exit>() {
                @Override
                public void handle(IVPN.Exit sig) {
                    context.exitApp();
                }
            });
            connection.addSigHandler(IVPN.ConnectionUpdated.class, context.getManager().getVPN(),
                    new DBusSigHandler<IVPN.ConnectionUpdated>() {
                        @Override
                        public void handle(IVPN.ConnectionUpdated sig) {
                            
                        }
                    });

            connection.addSigHandler(IVPN.GlobalConfigChange.class, context.getManager().getVPN(),
                    new DBusSigHandler<IVPN.GlobalConfigChange>() {
                        @Override
                        public void handle(IVPN.GlobalConfigChange sig) {
                            
                        }
                    });

            /* Listen for events on all existing connections */
            for (IVPNConnection vpnConnection : context.getManager().getVPNConnections()) {
                listenConnectionEvents(connection, vpnConnection);
            }

        } catch (DBusException dbe) {
            throw new IllegalStateException("Failed to configure.", dbe);
        }
    }

    @SuppressWarnings("unchecked")
    protected void removeConnectionEvents(AbstractConnection bus, Long id) throws DBusException {
        Map<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>> map = eventsMap.remove(id);
        if (map != null) {
            for (Map.Entry<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>> en : map.entrySet()) {
                bus.removeSigHandler((Class<DBusSignal>) en.getKey(), (DBusSigHandler<DBusSignal>) en.getValue());
            }
        }
    }

    protected <S extends DBusSignal, H extends DBusSigHandler<S>> H registerMappedEvent(IVPNConnection connection,
            Class<S> clazz, H handler) {
        Map<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>> map = eventsMap.get(connection.getId());
        if (map == null) {
            map = new HashMap<>();
            eventsMap.put(connection.getId(), map);
        }
        map.put(clazz, handler);
        return handler;
    }

    protected void listenConnectionEvents(AbstractConnection bus, IVPNConnection connection) throws DBusException {
        var id = connection.getId();
        /*
         * Client needs authorizing (first time configuration needed, or connection
         * failed with existing configuration)
         */
        bus.addSigHandler(IVPNConnection.Authorize.class, registerMappedEvent(connection, IVPNConnection.Authorize.class,
                new DBusSigHandler<IVPNConnection.Authorize>() {
                    @Override
                    public void handle(IVPNConnection.Authorize sig) {
                        if (sig.getId() != id)
                            return;
                        Mode authMode = Connection.Mode.valueOf(sig.getMode());
                    }
                }));
        bus.addSigHandler(IVPNConnection.Connecting.class, registerMappedEvent(connection,
                IVPNConnection.Connecting.class, new DBusSigHandler<IVPNConnection.Connecting>() {
                    @Override
                    public void handle(IVPNConnection.Connecting sig) {
                        if (sig.getId() != id)
                            return;
                    }
                }));
        bus.addSigHandler(IVPNConnection.Connected.class, registerMappedEvent(connection, IVPNConnection.Connected.class,
                new DBusSigHandler<IVPNConnection.Connected>() {
                    @Override
                    public void handle(IVPNConnection.Connected sig) {
                        IVPNConnection connection = context.getManager().getVPNConnection(sig.getId());
                        if (sig.getId() != id)
                            return;
                    }
                }));

        /* Failed to connect */
        bus.addSigHandler(IVPNConnection.Failed.class,
                registerMappedEvent(connection, IVPNConnection.Failed.class, new DBusSigHandler<IVPNConnection.Failed>() {
                    @Override
                    public void handle(IVPNConnection.Failed sig) {
                        if (sig.getId() != id)
                            return;
                        IVPNConnection connection = context.getManager().getVPNConnection(sig.getId());
                        
                    }
                }));

        /* Temporarily offline */
        bus.addSigHandler(IVPNConnection.TemporarilyOffline.class, registerMappedEvent(connection,
                IVPNConnection.TemporarilyOffline.class, new DBusSigHandler<IVPNConnection.TemporarilyOffline>() {
                    @Override
                    public void handle(IVPNConnection.TemporarilyOffline sig) {
                        if (sig.getId() != id)
                            return;
                        disconnectionReason = sig.getReason();
                    }
                }));

        /* Disconnected */
        bus.addSigHandler(IVPNConnection.Disconnected.class, registerMappedEvent(connection,
                IVPNConnection.Disconnected.class, new DBusSigHandler<IVPNConnection.Disconnected>() {
                    @Override
                    public void handle(IVPNConnection.Disconnected sig) {
                        if (sig.getId() != id)
                            return;
                        String thisDisconnectionReason = sig.getReason();
                        
                    }
                }));

        /* Disconnecting event */
        bus.addSigHandler(IVPNConnection.Disconnecting.class, registerMappedEvent(connection,
                IVPNConnection.Disconnecting.class, new DBusSigHandler<IVPNConnection.Disconnecting>() {
                    @Override
                    public void handle(IVPNConnection.Disconnecting sig) {
                        if (sig.getId() != id)
                            return;
                    }
                }));
    }

	@Override
    public Handle onConnectionAdded(Consumer<IVPNConnection> connection) {
        // TODO Auto-generated method stub
	    throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onConnectionUpdated(Consumer<IVPNConnection> connection) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onConnectionRemoved(Consumer<Long> connection) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onGlobalConfigChanged(BiConsumer<ConfigurationItem<?>, String> connection) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onVpnGone(Runnable onGone) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onVpnAvailable(Runnable onGone) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onAuthorize(Authorize authorize) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onConnecting(Consumer<IVPNConnection> connection) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onConnected(Consumer<IVPNConnection> connection) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onDisconnecting(Consumer<IVPNConnection> connection) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onDisconnected(BiConsumer<IVPNConnection, String> connection) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onTemporarilyOffline(BiConsumer<IVPNConnection, String> connection) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    @Override
    public Handle onFailure(Failure failure) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("TODO");
        
    }

    protected void disconnectFromBus() {
		conn.disconnect();
	}

	protected final void onInit() throws Exception {

		String fixedAddress = getServerDBusAddress(addressFile);
		if (conn == null || !conn.isConnected() || (StringUtils.isNotBlank(fixedAddress) && !fixedAddress.equals(conn.getAddress().toString()) )) {
			conn = createBusConnection();
			getLog().info("Got bus connection.");
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
		setVpn(newVpn);
		pingTask = scheduler.scheduleAtFixedRate(() -> {
			synchronized (initLock) {
				if (isBusAvailable()) {
					try {
						((VPN)getVPN()).ping();
					} catch (Exception e) {
						e.printStackTrace();
						busGone();
					}
				}
			}
		}, 5, 5, TimeUnit.SECONDS);

		certPromptHandler = new DBusSigHandler<>() {
			@Override
			public void handle(VPN.CertificatePrompt sig) {
				handleCertPromptRequest(sig);
			}
		};
		conn.addSigHandler(VPN.CertificatePrompt.class, (VPN)getVPN(),
				certPromptHandler);
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
				if (conn != null) {
					if(certPromptHandler != null) {
						try {
							conn.removeSigHandler(VPN.CertificatePrompt.class, vpn,
									certPromptHandler);
						} catch (DBusException e) {
						}
					}
					try {
						conn.disconnect();
					}
					catch(Exception e) {
					}
					finally {
						conn = null;
					}
				}
				vpn = null;
				getUpdateService().checkIfBusAvailable();
				for (BusLifecycleListener b : busLifecycleListeners) {
					b.busGone();
				}
			}

			/*
			 * Only really likely to happen with the embedded bus. As the service itself
			 * hosts it.
			 */
			scheduler.schedule(() -> {
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
