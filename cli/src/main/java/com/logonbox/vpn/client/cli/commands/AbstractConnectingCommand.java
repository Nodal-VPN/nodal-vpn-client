package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.dbus.VpnConnection;

import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.text.MessageFormat;

public abstract class AbstractConnectingCommand extends AbstractConnectionCommand {


   protected Integer doConnect(CLIContext cli, PrintWriter out, PrintWriter err, IVpnConnection connection)
            throws Exception {
        var status = Type.valueOf(connection.getStatus());
        if (status == Type.DISCONNECTED) {
            /* Create the connection, and set a shutdown hook for if the process is killed (softly)
             * by Ctrl+C or similar. If this code block actually completes, then remove the hook.
             */
            Runnable cleanUpRunner = () -> {
                connection.delete();
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
                            if (cli.isVerbose())
                                err.println(MessageFormat.format(CLI.BUNDLE.getString("error.failedToConnect"), connection.getUri(true)));
                            cli.getConsole().flush();
                            return 1;
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
