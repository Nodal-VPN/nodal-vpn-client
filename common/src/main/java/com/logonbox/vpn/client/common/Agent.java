package com.logonbox.vpn.client.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public abstract class Agent implements Closeable {
	
	static Logger LOG = LoggerFactory.getLogger(Agent.class);

	private final ServerSocket ss;
	private final Thread thead;
    private final Connection connection;

	public Agent(Connection connection) throws UnknownHostException, IOException {
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
		try(var in = new BufferedReader(new InputStreamReader(accept.getInputStream(), "UTF-8"))) {
		    String line;
		    var cfg = new StringBuilder();
		    var boundary = in.readLine();
		    while( ( line = in.readLine() ) != null) {
                if(line.equals(boundary)) {
                    break;
                }
                else {
    		        if(!cfg.isEmpty())
    		            cfg.append(System.lineSeparator());
    		        cfg.append(line);
                }
		    }
		    
            var signature = in.readLine();
            
            /* TODO check signature */
            update(connection, cfg.toString());
		}
	}

	@Override
	public void close() throws IOException {
		LOG.info("Closing agent listening on {}", ss.getLocalPort());
		Thread.interrupted();
		ss.close();
	}
	
	protected abstract void update(Connection connection, String configuration);
}
