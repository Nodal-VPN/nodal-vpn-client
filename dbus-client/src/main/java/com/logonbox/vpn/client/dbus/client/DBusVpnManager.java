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
package com.logonbox.vpn.client.dbus.client;

import com.logonbox.vpn.client.common.AbstractVpnManager;
import com.logonbox.vpn.client.common.AppContext;
import com.logonbox.vpn.client.common.AuthMethod;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IRemoteUI;
import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.dbus.BusFactory;
import com.logonbox.vpn.client.common.dbus.RemoteUI;
import com.logonbox.vpn.client.common.dbus.RemoteUI.ConfirmedExit;
import com.logonbox.vpn.client.common.dbus.VPN;
import com.logonbox.vpn.client.common.dbus.VpnConnection;

import org.freedesktop.dbus.connections.IDisconnectCallback;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.AddressResolvingException;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
//import org.freedesktop.dbus.interfaces.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public final class DBusVpnManager extends AbstractVpnManager<VpnConnection> {
    private final static Logger log = LoggerFactory.getLogger(DBusVpnManager.class);
    
    public final static class Builder {
        private Optional<String> busAddress = Optional.empty();
        private Optional<Boolean> sessionBus = Optional.empty();
        private boolean supportsAuthorization;
        private final AppContext<VpnConnection> app;
        
        public Builder(AppContext<VpnConnection> app) {
            this.app = app;
        }
        
        public Builder withBusAddress(String busAddress) {
            return withBusAddress(Optional.of(busAddress));
        }
        
        public Builder withBusAddress(Optional<String> busAddress) {
            this.busAddress = busAddress;
            return this;
        }

        public Builder withSessionBus() {
            return withSessionBus(true);
        }
        
        public Builder withSessionBus(boolean sessionBus) {
            this.sessionBus = Optional.of(sessionBus);
            return this;
        }

        public Builder withAuthorization() {
            return withAuthorization(true);
        }
        
        public Builder withAuthorization(boolean authorization) {
            this.supportsAuthorization = authorization;
            return this;
        }
        
        public DBusVpnManager build() {
            return new DBusVpnManager(this);
        }
    }

	final static int DEFAULT_TIMEOUT = 10000;

	private static final String BUS_NAME = "com.logonbox.vpn";
	private static final String ROOT_OBJECT_PATH = "/com/logonbox/vpn";

	private final String busAddress;
	private final Optional<Boolean> sessionBus;
    private final boolean supportsAuthorization;
    private final AppContext<VpnConnection> app;
	
    private DBusConnection conn;
    private Object initLock = new Object();
	private ScheduledFuture<?> pingTask;
    private final Map<Long, List<AutoCloseable>> eventsMap = new HashMap<>();
    private boolean brokerAvailable;
    private List<AutoCloseable> handles = Collections.emptyList();
    private List<AutoCloseable> altHandles = Collections.emptyList();
    private DBusConnection altConn;
    private IVpn<VpnConnection> vpn;
    private boolean registered;

	private DBusVpnManager(Builder bldr) {
	    super();
	    
	    this.app = bldr.app;
	    this.supportsAuthorization = bldr.supportsAuthorization;
	    this.sessionBus = bldr.sessionBus;
	    this.busAddress = bldr.busAddress.orElse(null);
	    
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (isBackendAvailable()) {
                    try {
                        ((VPN)getVpnOrFail()).deregister();
                    } catch (Exception e) {
                        log.warn("De-registrating failed. Maybe service is already gone.");
                    }
                }
            }
        });
	}

    public DBusConnection getAltBus() {
        synchronized (initLock) {
//            lazyInit();
            if (altConn == null) {
                try {
                    altConn = new BusFactory.Builder().
                        withSessionBus().
                        withConnectionBuilderConfigurator(bldr ->  {
                            bldr.transportConfig().withTimeout(500);
                            bldr.withShared(false); 
                        }).
                        build().
                        connection();
                } catch (DBusException | IOException e) {
                    throw new IllegalStateException("Could not get alternate bus.", e);
                }
            }
            return altConn;
        }
    }

	@Override
    public void confirmExit() {
	    try {
//	        lazyInit();
            getBus().sendMessage(new ConfirmedExit(RemoteUI.OBJECT_PATH));
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to confirm exit.");
        }
    }

    @Override
    public final boolean isBackendAvailable() {
        return brokerAvailable;
    }

	public final DBusConnection getBus() {
//        lazyInit();
        return conn;
	}

	@Override
    public final void checkVpnManagerAvailable() throws IllegalStateException {
	    try {
//	        lazyInit();
	        ((VPN)getVpnOrFail()).ping();
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

    protected final void listenConnectionEvents(VpnConnection connection)  {        
        var id = connection.getId();
        try {
            eventsMap.put(id, Arrays.asList(
                conn.addSigHandler(VpnConnection.Authorize.class, (VpnConnection)connection, (sig) -> {
                    if(sig.isLegacy()) {
                        onAuthorize.forEach(a -> a.authorize(connection, sig.getUri(), Mode.valueOf(sig.getMode())));   
                    }
                    else {
                        var l = new ArrayList<AuthMethod>();
                        for(var m : sig.getMode().split(",")) {
                            try {
                                l.add(AuthMethod.valueOf(m));
                            }
                            catch(Exception e) {
                                log.warn("Failed to parse mode {}", m);
                            }
                        }
                        onAuthorize.forEach(a -> a.authorize(connection, sig.getUri(), Mode.MODERN, l.toArray(new AuthMethod[0])));
                    }
                }),
                conn.addSigHandler(VpnConnection.Connecting.class, (VpnConnection)connection, (sig) -> {
                    onConnecting.forEach(a -> a.accept(connection));
                }),
                conn.addSigHandler(VpnConnection.Connected.class, (VpnConnection)connection, (sig) -> {
                    onConnected.forEach(a -> a.accept(connection));
                }),
                conn.addSigHandler(VpnConnection.Disconnecting.class, (VpnConnection)connection, (sig) -> {
                    onDisconnecting.forEach(a -> a.accept(connection, sig.getReason()));
                }),
                conn.addSigHandler(VpnConnection.Disconnected.class, (VpnConnection)connection, (sig) -> {
                    onDisconnected.forEach(a -> a.accept(connection, sig.getReason()));
                }),
                conn.addSigHandler(VpnConnection.TemporarilyOffline.class, (VpnConnection)connection, (sig) -> {
                    onTemporarilyOffline.forEach(a -> a.accept(connection, sig.getReason()));
                }),
                conn.addSigHandler(VpnConnection.Blocked.class, (VpnConnection)connection, (sig) -> {
                    onBlocked.forEach(a -> a.accept(connection));
                }),
                conn.addSigHandler(VpnConnection.Failed.class, (VpnConnection)connection, (sig) -> {
                    onFailure.forEach(a -> a.failure(connection, sig.getReason(), sig.getCause(), sig.getTrace()));
                })
            ));
        }
        catch(DBusException dbe) {
            throw new IllegalStateException("Failed to configure signals.", dbe);
        }
    }

	protected final void onInit() throws Exception {

		if (conn == null || !conn.isConnected()) {
			conn = createBusConnection();
			 log.debug("Got bus connection.");
			brokerAvailable = true;
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
		var busAddress = this.busAddress;
		
		var bldr = new BusFactory.Builder();
        if(Utils.isNotBlank(busAddress)) {
            bldr.withAddress(busAddress);
        }
        else {
            sessionBus.ifPresentOrElse(sb -> {
                if(sb)
                    bldr.withSessionBus();
                else
                    bldr.withSystemBus();
            }, () -> bldr.withSystemBus());
        }
		
        return bldr.
		        build().
		        connection();
		
	}
	
	public boolean isRegistered() {
	    return registered;
	}
    
    protected final void setVPN(IVpn<VpnConnection> vpn) {
        this.vpn = vpn;
        if(vpn == null)
            registered = false;
    }

	protected DBusConnectionBuilder configureBuilder(DBusConnectionBuilder builder) {
		builder.withShared(false);
		builder.transportConfig().
		    withRegisterSelf(true).
            withTimeout(500);
		return builder;
	}

	protected void onLazyInit() throws Exception {
		log.info("Trying connect to DBus");
		try {
			init();
		} catch (UnknownObject | DBusException | ServiceUnknown dbe) {
			busGone();
		} 
	}

    protected final void init() throws Exception {

        if (vpn != null) {
            log.debug("Call to init when already have bus.");
            return;
        }

        if (log.isDebugEnabled())
            log.debug(String.format("Using update service %s", app.getUpdateService().getClass().getName()));

        onInit();

        /* Create cert manager */
        log.info("Cert manager: {}", app.getCertManager());
    }
    
    @Override
    public final void start() {
        if (vpn == null) {
            log.info("Trying to obtain Vpn Manager");
            try {
                onLazyInit();   
            }
            catch (RuntimeException re) {
                log.error("Failed to initialise.", re);
                throw re;
            } catch (Exception e) {
                log.error("Failed to initialise.", e);
                throw new IllegalStateException("Failed to initialize.", e);
            }
        }
    }

	private void loadRemote() throws DBusException {
		log.debug("Loading remote objects.");
		var newVpn = conn.getExportedObject(BUS_NAME, ROOT_OBJECT_PATH, VPN.class);
		log.info("Got remote object, registering with DBus.");
		log.info("Registering with VPN service.");
		newVpn.register(app.getEffectiveUser(), app.isInteractive(), supportsAuthorization);
		registered = true;
		log.info("Registered with VPN service.");
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
                    sig -> app.shutdown(false)),
            conn.addSigHandler(VPN.CertificatePrompt.class, newVpn, 
                    this::handleCertPromptRequest),
            conn.addSigHandler(VPN.ConnectionRemoving.class, newVpn, 
                    sig ->  { 
                        try {
                            var conx = newVpn.getConnection(sig.getId());
                            onConnectionRemoving.forEach(r -> r.accept(conx)); 
                        }
                        catch(Exception e) {
                            log.warn("Failed to get connection, probably got removing event after the connection was removed. {}", e.getMessage());
                        }
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
		try {
	        var altBus = getAltBus();
            altHandles = Arrays.asList(
                altBus.addSigHandler(RemoteUI.ConfirmedExit.class, RemoteUI.OBJECT_PATH, sig -> {
                    onConfirmedExit.forEach(r -> r.run());
                })
            );
		}
		catch(DBusException | AddressResolvingException dbe) {
		    altHandles = Collections.emptyList();
		}

        /* Listen for events on all existing connections */
		for (var vpnConnection : getVpnOrFail().getConnections()) {
		      listenConnectionEvents(vpnConnection);
		}

        onVpnAvailable.forEach(Runnable::run);
		
		pingTask = app.getScheduler().scheduleAtFixedRate(() -> {
			synchronized (initLock) {
				if (isBackendAvailable()) {
					try {
						((VPN)getVpnOrFail()).ping();
					} catch (Exception e) {
						busGone();
					}
				}
			}
		}, 5, 5, TimeUnit.SECONDS);
	}

	protected void handleCertPromptRequest(VPN.CertificatePrompt sig) {
		var cm = app.getCertManager();
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
			log.info("Stopping pinging.");
			pingTask.cancel(false);
			pingTask = null;
		}
	}

	private final void busGone() {
		synchronized (initLock) {
			cancelPingTask();

            var wasAvailable = brokerAvailable;
            
			if (brokerAvailable) {

				brokerAvailable = false;
				
				onVpnGone.forEach(Runnable::run);
				setVPN(null);
			}
			
			if(wasAvailable) {
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
                handles.forEach(h -> {
                    try {
                        h.close();
                    } catch (Exception e) {
                    }
                });
			}

            if (conn != null) {
                try {
                    conn.disconnect();
                }
                catch(Exception e) {
                }
                finally {
                    vpn = null;
                    conn = null;
                }
            }

			/*
			 * Only really likely to happen with the embedded bus. As the service itself
			 * hosts it.
			 */
			app.getScheduler().schedule(() -> {
				synchronized (initLock) {
					try {
						init();
					} catch (NoReply | DBusException | ServiceUnknown | UnknownObject dbe) {
						if (log.isDebugEnabled())
							log.debug("Init() failed, retrying");
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

    @Override
    public Optional<IVpn<VpnConnection>> getVpn() {
//        lazyInit();
        return Optional.ofNullable(vpn);
    }

    @Override
    public Optional<IRemoteUI> getUserInterface() {
        try {
//            lazyInit();
            var obj = getAltBus().getRemoteObject(RemoteUI.BUS_NAME, RemoteUI.OBJECT_PATH, RemoteUI.class);
            obj.ping();
            return Optional.of(obj);
        } catch (DBusExecutionException su) {
            return Optional.empty();
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to send remote call.");
        }
    }
}
