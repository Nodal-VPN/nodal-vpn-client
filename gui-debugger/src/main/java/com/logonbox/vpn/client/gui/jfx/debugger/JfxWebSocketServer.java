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
package com.logonbox.vpn.client.gui.jfx.debugger;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class JfxWebSocketServer extends WebSocketServer {
    public static final String WEB_SOCKET_RESOURCE = "/?%s";
    private static final String CHROME_DEBUG_URL = "devtools://devtools/bundled/inspector.html?ws=localhost:";

    final private HashMap<String, WebSocket> myConnections = new HashMap<>();
    final private HashMap<String, JfxDebuggerConnector> myServers = new HashMap<>();
    final private HashMap<JfxDebuggerConnector, String> myServerIds = new HashMap<>();
    private Consumer<Throwable> onFailure;
    private Consumer<JfxWebSocketServer> onStart;
    private final AtomicInteger myServerUseCount = new AtomicInteger(0);
    final Logger LOG = LoggerFactory.getLogger(JfxWebSocketServer.class.getName());

    public JfxWebSocketServer(InetSocketAddress address, Consumer<Throwable> onFailure, Consumer<JfxWebSocketServer> onStart) {
        super(address, 4);
        setReuseAddr(true);
        this.onFailure = onFailure;
        this.onStart = onStart;
    }

    public boolean isDebuggerConnected(JfxDebuggerConnector server) {
        String resourceId = myServerIds.get(server);
        WebSocket conn = myConnections.get(resourceId);
        return conn != null;
    }

    public boolean send(JfxDebuggerConnector server, String data) throws NotYetConnectedException {
        String resourceId = myServerIds.get(server);
        WebSocket conn = myConnections.get(resourceId);
        if (conn == null) {
            return false;
        }

        LOG.info("sending to " + conn.getRemoteSocketAddress() + ": " + data);
        try {
            conn.send(data);
        } catch (WebsocketNotConnectedException e) {
            myConnections.put(resourceId, null);
            return false;
        }
        return true;
    }

    public String getDebugUrl(JfxDebuggerConnector server) {
        // Chrome won't launch first session if we have a query string
        return CHROME_DEBUG_URL + super.getPort() + myServerIds.get(server);
    }

    public void addServer(JfxDebuggerConnector debugServer, int instanceID) {
        String resourceId = instanceID == 0 ? "/" : String.format(WEB_SOCKET_RESOURCE, instanceID);
        LOG.info("Adding debug server " + resourceId);
        if (myServers.containsKey(resourceId)) {
            throw new IllegalStateException("Resource id " + resourceId + " is already handled by " + myServers.get(resourceId));
        }
        myConnections.put(resourceId, null);
        myServers.put(resourceId, debugServer);
        myServerIds.put(debugServer, resourceId);
        myServerUseCount.incrementAndGet();
    }

    public boolean removeServer(JfxDebuggerConnector server) {
        String resourceId = myServerIds.get(server);
        if (resourceId != null) {
            WebSocket conn = myConnections.get(resourceId);
            if (conn != null) {
                conn.close();
            }

            myServerIds.remove(server);
            myConnections.remove(resourceId);
            myServers.remove(resourceId);
            int serverUseCount = myServerUseCount.decrementAndGet();
            if (serverUseCount < 0) {
            	LOG.info("Internal error: server use count <0");
                myServerUseCount.set(0);
            }
        }
        return myServerUseCount.get() <= 0;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String resourceId = conn.getResourceDescriptor();

        if (!myConnections.containsKey(resourceId)) {
            LOG.info("new connection to " + conn.getRemoteSocketAddress() + " rejected");
            conn.close(CloseFrame.REFUSE, "No JavaFX WebView Debugger Instance");
        } else {
            WebSocket otherConn = myConnections.get(resourceId);
            if (otherConn != null) {
                // We will disconnect the other
                LOG.info("closing old connection to " + conn.getRemoteSocketAddress());
                otherConn.close(CloseFrame.GOING_AWAY, "New Dev Tools connected");
            }

            myConnections.put(resourceId, conn);
            if (myServers.containsKey(resourceId)) {
                myServers.get(resourceId).onOpen();
            }
            LOG.info("new connection to " + conn.getRemoteSocketAddress());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String resourceId = conn.getResourceDescriptor();

        WebSocket otherConn = myConnections.get(resourceId);
        if (otherConn == conn) {
            myConnections.put(resourceId, null);
            if (myServers.containsKey(resourceId)) {
                myServers.get(resourceId).onClosed(code, reason, remote);
            }
            LOG.info("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String resourceId = conn.getResourceDescriptor();

        if (myServers.containsKey(resourceId)) {
            myServers.get(resourceId).sendMessageToBrowser(message);
            LOG.info("received from " + conn.getRemoteSocketAddress() + ": " + message);
        } else {
            LOG.info("connection to " + conn.getRemoteSocketAddress() + " closed");
            conn.close();
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
    	LOG.info("received ByteBuffer from " + conn.getRemoteSocketAddress());
    }

    @Override
    public void stop(final int timeout) throws InterruptedException {
        try {
            super.stop(timeout);
        } catch (Exception ex) {
            // nothing to do, handleFatal will clean up
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
                LOG.info("an error occurred on connection null :", ex);
        } else {
                LOG.info("an error occurred on connection " + conn.getRemoteSocketAddress() + ":", ex);
        }

        onStart = null;
        if (onFailure != null) {
            Consumer<Throwable> failure = onFailure;
            onFailure = null;
            failure.accept(ex);
        }
    }

    @Override
    public void onStart() {
        onFailure = null;
        if (onStart != null) {
            Consumer<JfxWebSocketServer> start = onStart;
            onStart = null;
            start.accept(this);
        }
        LOG.info("server started successfully");
    }
}
