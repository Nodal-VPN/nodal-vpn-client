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
package com.logonbox.vpn.client.service;

import com.jadaptive.nodal.core.lib.NoHandshakeException;
import com.jadaptive.nodal.core.lib.StartRequest;
import com.jadaptive.nodal.core.lib.VpnAdapter;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.Agent;
import com.logonbox.vpn.client.common.Agent.AgentCommand;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.api.IVpnConnection;

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
    private Optional<Agent> agent = Optional.empty();
    private final BiConsumer<VPNSession<CONX>, AgentCommand> onUpdate;

    public VPNSession(Connection connection, LocalContext<CONX> localContext, BiConsumer<VPNSession<CONX>, AgentCommand> onUpdate) {
        this(connection, localContext, null, onUpdate);
    }

    public VPNSession(Connection connection, LocalContext<CONX> localContext, VpnAdapter session, BiConsumer<VPNSession<CONX>, AgentCommand> onUpdate) {
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
        agent.ifPresent(a -> {
            try {
                a.close();
            }
            catch(IOException ioe) {
                log.warn("Failed to close agent.", ioe);
            }            
        });
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
                return alive;
            } catch (Exception e) {
                log.debug("Failed to test if alive, assuming not.", e);
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
            agent = Optional.of(new Agent(connection, (cmd) -> {
                onUpdate.accept(this, cmd);
            }));
            log.info("Agent opened on port {}", agent.get().getPort());
        }
        catch(Exception e) {
            log.error("Failed to setup agent, server will not be able to communicate configuration updates to this peer.", e);
        }
    }
    
    public Optional<Agent> getAgent() {
        return agent;
    }

    public Optional<VpnAdapter> getSession() {
        return session;
    }

    public void setTask(ScheduledFuture<?> task) {
        this.task = task;
    }
}
