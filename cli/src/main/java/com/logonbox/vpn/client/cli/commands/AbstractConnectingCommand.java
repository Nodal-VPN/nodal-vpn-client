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
package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.dbus.VpnConnection;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;

public abstract class AbstractConnectingCommand extends AbstractConnectionCommand {


   protected Integer doConnect(CLIContext cli, PrintWriter out, PrintWriter err, IVpnConnection connection, boolean deleteOnFail)
            throws Exception {
        var status = Type.valueOf(connection.getStatus());
        if (status == Type.DISCONNECTED) {
            /* Create the connection, and set a shutdown hook for if the process is killed (softly)
             * by Ctrl+C or similar. If this code block actually completes, then remove the hook.
             */
            Runnable cleanUpRunner = () -> {
                if(deleteOnFail)
                    connection.delete();
                else
                    connection.disconnect("Cancelled.");
            }; 
            var cleanUp = new Thread(cleanUpRunner, "RemoveCreatedConnectionOnError");
            Runtime.getRuntime().addShutdownHook(cleanUp);
            try {
               
                try (var stateHelper = new StateHelper((VpnConnection) connection, cli.getVpnManager())) {
                    if (cli.isVerbose()) {
                        out.println(MessageFormat.format(CLI.BUNDLE.getString("info.connecting"), connection.getUri(true)));
                        cli.getConsole().flush();
                    }
                    stateHelper.on(Type.AUTHORIZING, (state, mode) -> {
                        if(mode.equals(Mode.SERVICE) || mode.equals(Mode.MODERN)) {
                            register(cli, connection, out, err);
                        }
                        else {
                            throw new UnsupportedOperationException(MessageFormat.format(CLI.BUNDLE.getString("error.badAuthMode"), mode));
                        }
                    });
                    stateHelper.start(Type.CONNECTING);
                    connection.connect();
                    try {
                        status = stateHelper.waitForState(Type.CONNECTED, Type.DISCONNECTED);
                        if (status == Type.CONNECTED) {
                            if (cli.isVerbose())
                                out.println(CLI.BUNDLE.getString("info.ready"));
                            cli.getConsole().flush();
                            return 0;
                        } else {
                            throw new IOException(MessageFormat.format(CLI.BUNDLE.getString("error.failedToCnnect"), connection.getUri(true)));
                        }
                    }
                    catch(Exception e) {
                        disconnect(connection, cli);
                        LoggerFactory.getLogger(AbstractConnectionCommand.class).info("Connection failed.", e);
                        throw e;
                    }
                }
            }
            catch(Exception e) {
                cleanUpRunner.run();
                throw e;
            }
            finally {
                Runtime.getRuntime().removeShutdownHook(cleanUp);
            }
           
        } else {
            throw new IllegalStateException(MessageFormat.format(CLI.BUNDLE.getString("error.alreadyConnected"), connection.getUri(true)));
        }
    }
}
