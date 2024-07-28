package com.logonbox.vpn.client.service;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.Agent;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.drivers.lib.NoHandshakeException;
import com.logonbox.vpn.drivers.lib.StartRequest;
import com.logonbox.vpn.drivers.lib.VpnAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BiConsumer;

public class VPNSession<CONX extends IVpnConnection> implements Closeable {

    public static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("wireguard.maxInterfaces", "10"));

    static Logger log = LoggerFactory.getLogger(VPNSession.class);

    private final LocalContext<CONX> localContext;
    private ScheduledFuture<?> task;
    private boolean reconnect;
    private Optional<VpnAdapter> session = Optional.empty();
    private final Connection connection;
    private Agent agent;
    private final BiConsumer<Connection, String> onUpdate;

    public VPNSession(Connection connection, LocalContext<CONX> localContext, BiConsumer<Connection, String> onUpdate) {
        this(connection, localContext, null, onUpdate);
    }

    public VPNSession(Connection connection, LocalContext<CONX> localContext, VpnAdapter session, BiConsumer<Connection, String> onUpdate) {
        this.localContext = localContext;
        this.connection = connection;
        this.session = Optional.ofNullable(session);
        this.onUpdate = onUpdate;
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isReconnect() {
        return reconnect;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    public LocalContext<CONX> getLocalContext() {
        return localContext;
    }

    @Override
    public void close() throws IOException {
        if(agent != null) {
            try {
                agent.close();
            }
            catch(IOException ioe) {
                log.warn("Failed to close agent.", ioe);
            }
        }
        if (task != null) {
            task.cancel(false);
        }
        if (session.isPresent()) {
            session.get().close();
        }
    }

    public Optional<String> getInterfaceName() {
        return session.isPresent() ? (Optional.of(session.get().address().name()))
                : Optional.empty();
    }

    /**
     * Get if a session is actually an active connect. If this is acting as a server,
     * then just the interface need be up for this to return <code>true</code>, other
     * wise active {@link  (handshake has occurred and
     * has not timed out).
     * 
     * @return up
     */
    public boolean isAlive() {
        if(session.isPresent()) {
            try {
                var sessionObj = session.get();
                var ipName = getInterfaceName().get();
                var platform = localContext.getPlatformService();
                var lastHandshake = platform.getLatestHandshake(sessionObj.address(), connection.getPublicKey());
                var now = Instant.now();
                var alive = !lastHandshake.isBefore(now.minus(platform.context().configuration().handshakeTimeout()));
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Checking if {} ({}) is still alive. Now is {}, Latest Handshake is {}, Timeout secs is {}, Alive is {}",
                            ipName, connection.getPublicKey(), now, lastHandshake,
                            platform.context().configuration().handshakeTimeout().toSeconds(), alive);
                }
                log.info(
                        "Checking if {} ({}) is still alive. Now is {}, Latest Handshake is {}, Timeout secs is {}, Alive is {}",
                        ipName, connection.getPublicKey(), now, lastHandshake,
                        platform.context().configuration().handshakeTimeout().toSeconds(), alive);
                return alive;
            } catch (Exception e) {
                //log.debug("Failed to test if alive, assuming not.", e);
                log.info("Failed to test if alive, assuming not.", e);
                return false;
            }
        }
        else {
            log.info("No session, so not alive");
            return false;
        }

    }

    public void open() throws IOException {

        var vpnConnection = connection;
        if (log.isInfoEnabled()) {
            log.info(String.format("Connecting to %s (owned by %s)", vpnConnection.getUri(true),
                    vpnConnection.getOwnerOrCurrent()));
        }
        if (!vpnConnection.isAuthorized()) {
            throw new ReauthorizeException("Requires authorization.");
        }

        try {
            session = Optional.of(getLocalContext().getPlatformService().start(
                    new StartRequest.Builder(vpnConnection).
                    withPeer(vpnConnection.firstPeer()).
                    build()
            ));
        } catch (NoHandshakeException nse) {
            throw new ReauthorizeException(nse.getMessage());
        }

        
      try {
          agent = new Agent(connection) {
            @Override
            protected void update(Connection connection, String configuration) {
                onUpdate.accept(connection, configuration);
            } 
          };
      }
      catch(Exception e) {
          log.error("Failed to setup agent, server will not be able to communicate configuration updates to this peer.", e);
      }
    }

    public Optional<VpnAdapter> getSession() {
        return session;
    }

    public void setTask(ScheduledFuture<?> task) {
        this.task = task;
    }
}
