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

import com.sun.javafx.scene.web.Debugger;
import javafx.application.Platform;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.NotYetConnectedException;
import java.util.HashMap;
import java.util.function.Consumer;

public class DevToolsDebuggerServer implements JfxDebuggerConnector {
    final static HashMap<Integer, JfxWebSocketServer> ourServerMap = new HashMap<>();

    final Debugger myDebugger;
    JfxWebSocketServer myServer;
    final Logger LOG = LoggerFactory.getLogger(DevToolsDebuggerServer.class.getName());

    public DevToolsDebuggerServer(Debugger debugger, int debuggerPort, final int instanceId,  Consumer<Throwable> onFailure,  Runnable onStart) {
        myDebugger = debugger;
        boolean freshStart = false;

        JfxWebSocketServer jfxWebSocketServer;

        synchronized (ourServerMap) {
            jfxWebSocketServer = ourServerMap.get(debuggerPort);

            if (jfxWebSocketServer == null) {
                jfxWebSocketServer = new JfxWebSocketServer(new InetSocketAddress("localhost", debuggerPort), (ex) -> {
                    synchronized (ourServerMap) {
                        if (ourServerMap.get(debuggerPort).removeServer(this)) {
                            // unused
                            ourServerMap.remove(debuggerPort);
                        }
                    }

                    if (onFailure != null) {
                        onFailure.accept(ex);
                    }
                }, (server) -> {
                    myServer = server;
                    initDebugger(instanceId, onStart);
                });

                ourServerMap.put(debuggerPort, jfxWebSocketServer);
                freshStart = true;
            }
        }

        jfxWebSocketServer.addServer(this, instanceId);

        if (freshStart) {
            jfxWebSocketServer.start();
        } else {
            myServer = jfxWebSocketServer;
            initDebugger(instanceId, onStart);
        }
    }

    private void initDebugger(int instanceId,  Runnable onStart) {
        Platform.runLater(() -> {
            myDebugger.setEnabled(true);
            myDebugger.sendMessage("{\"id\" : -1, \"method\" : \"Network.enable\"}");

            this.myDebugger.setMessageCallback(data -> {
                try {
                    myServer.send(this, data);
                } catch (NotYetConnectedException e) {
                    e.printStackTrace();
                }
                return null;
            });

                String remoteUrl = getDebugUrl();
                LOG.info("Debug session created. Debug URL: " + remoteUrl);

            if (onStart != null) {
                onStart.run();
            }
        });
    }

    public boolean isDebuggerConnected() {
        return myServer.isDebuggerConnected(this);
    }

    
    public String getDebugUrl() {
        // Chrome won't launch first session if we have a query string
        return myServer != null ? myServer.getDebugUrl(this) : "";
    }

    public void stopDebugServer(Consumer<Boolean> onStopped) {
        if (myServer != null) {
            Platform.runLater(() -> {
                Runnable action = () -> {
                        String remoteUrl = getDebugUrl();
                        LOG.info("Debug session stopped for URL: " + remoteUrl);

                    boolean handled = false;
                    boolean unusedServer;

                    synchronized (ourServerMap) {
                        unusedServer = myServer.removeServer(this);
                        if (unusedServer) {
                            ourServerMap.remove(myServer.getAddress().getPort());
                        }
                    }

                    if (unusedServer) {
                        // shutdown
                        try {
                            myServer.stop(1000);
                            myServer = null;
                            LOG.info("WebView debug server shutdown.");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            handled = true;
                            // instance removed and server stopped
                            onStopped.accept(true);
                        }
                    }

                    if (!handled) {
                        // instance removed, server running
                        onStopped.accept(false);
                    }
                };

                if (myDebugger instanceof JfxDebuggerProxy) {
                    // release from break point or it has a tendency to core dump
                    ((JfxDebuggerProxy) myDebugger).removeAllBreakpoints(() -> {
                        ((JfxDebuggerProxy) myDebugger).releaseDebugger(true, () -> {
                            // doing this if was stopped at a break point will core dump the whole app
                            myDebugger.setEnabled(false);
                            myDebugger.setMessageCallback(null);
                            action.run();
                        });
                    });
                } else {
                    myDebugger.setEnabled(false);
                    myDebugger.setMessageCallback(null);
                    action.run();
                }
            });
        } else {
            // have not idea since this instance was already disconnected
            onStopped.accept(null);
        }
    }

    @Override
    public void onOpen() {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).onOpen();
        }
    }

    @Override
    public void pageReloading() {
        if (myDebugger instanceof JfxDebuggerProxy && isDebuggerConnected()) {
            ((JfxDebuggerProxy) myDebugger).pageReloading();
        }
    }

    @Override
    public void reloadPage() {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).reloadPage();
        }
    }

    @Override
    public void onClosed(final int code, final String reason, final boolean remote) {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).onClosed(code, reason, remote);
        }
    }

    @Override
    public void log(final String type, final long timestamp, final JSObject args) {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).log(type, timestamp, args);
        }
    }

    @Override
    public void setDebugOnLoad(final DebugOnLoad debugOnLoad) {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).setDebugOnLoad(debugOnLoad);
        }
    }

    @Override
    public DebugOnLoad getDebugOnLoad() {
        if (myDebugger instanceof JfxDebuggerProxy) {
            return ((JfxDebuggerProxy) myDebugger).getDebugOnLoad();
        }
        return DebugOnLoad.NONE;
    }

    @Override
    public void debugBreak() {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).debugBreak();
        }
    }

    @Override
    public boolean isDebuggerPaused() {
        if (myDebugger instanceof JfxDebuggerProxy) {
            return ((JfxDebuggerProxy) myDebugger).isDebuggerPaused();
        }
        return false;
    }

    @Override
    public void releaseDebugger(final boolean shuttingDown,  Runnable runnable) {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).releaseDebugger(shuttingDown, runnable);
        }
    }

    @Override
    public void removeAllBreakpoints( Runnable runnable) {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).removeAllBreakpoints(runnable);
        }
    }

    @Override
    public void pageLoadComplete() {
        if (myDebugger instanceof JfxDebuggerProxy) {
            ((JfxDebuggerProxy) myDebugger).pageLoadComplete();
        }
    }

    public void sendMessageToBrowser(final String data) {
        Platform.runLater(() -> myDebugger.sendMessage(data));
    }
}
