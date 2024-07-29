package com.logonbox.vpn.client.common;

import com.logonbox.vpn.drivers.lib.util.Keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;

public final class Agent implements Closeable {
    
    public record AgentCommand(Connection connection, Command command, String[] args) {}
    
    @FunctionalInterface
    public interface AgentListener {
        void command(AgentCommand command);
    }
    
    public enum Command {
        DISCONNECT,
        DELETE,
        UPDATE
    }
	
	static Logger LOG = LoggerFactory.getLogger(Agent.class);

	private final ServerSocket ss;
	private final Thread thead;
    private final AgentListener onCommand;
    private final Connection connection;

	public Agent(Connection connection, AgentListener onCommand) throws UnknownHostException, IOException {
	    this.onCommand = onCommand;
	    this.connection = connection;
	    
		ss = new ServerSocket(0, 1, InetAddress.getByName(connection.getAddress()));
		LOG.info("Agent listening on {}", ss.getLocalPort());
		thead = new Thread(() -> {
			try {
				while(true) {
					connection(ss.accept());
				}
			}
			catch(Exception e) {
				LOG.error("Agent thread died.", e);
			}
		}, "Agent-" + connection.getAddress());
		thead.start();
				
	}
	
	public int getPort() {
	    return ss.getLocalPort();
	}

	private void connection(Socket accept) throws IOException {
		LOG.info("Got agent connection from {}", accept.getRemoteSocketAddress());
		try(var in = new DataInputStream(accept.getInputStream())) {
		    while(true) {
		        /* Each command enclosed in a simple signature wrapper */
		        var payloadSize = in.readInt();
		        var payload = new byte[payloadSize];
		        in.read(payload);

                var sigSize = in.readShort();
                var sig = new byte[sigSize];
                in.read(sig);
                
                var pubkey = Base64.getDecoder().decode(connection.getPublicKey());
                if(!Keys.verify(pubkey, payload, sig))
                    throw new IllegalArgumentException("Command doesn't match signature.");
                
                var innerIn = new DataInputStream(new ByteArrayInputStream(payload));
		        
	            var cmd = Command.valueOf(innerIn.readUTF());
	            
	            var rnd = innerIn.readShort();
	            var data = new byte[rnd];
	            innerIn.read(data);
	            
	            var args = new ArrayList<String>();
	            var argSize = innerIn.readShort();
	            for(int i = 0 ; i < argSize;  i++) {
	                args.add(innerIn.readUTF());
	            }

                onCommand.command(new AgentCommand(connection, cmd, args.toArray(new String[0])));
		    }
		}
		catch(EOFException eofe) {
		    // OK
		}
		finally {
		    accept.close();
		}
	}

	@Override
	public void close() throws IOException {
		LOG.info("Closing agent listening on {}", ss.getLocalPort());
		Thread.interrupted();
		ss.close();
	}
}
