package com.logonbox.vpn.client.service;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.Connection;

public class Agent implements Closeable {
	
	static Logger LOG = LoggerFactory.getLogger(Agent.class);

	private ServerSocket ss;
	private Thread thead;

	public Agent(Connection connection) throws UnknownHostException, IOException {
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

	private void connection(Socket accept) {
		LOG.info("Got agent connection from {}", accept.getRemoteSocketAddress());
	}

	@Override
	public void close() throws IOException {
		LOG.info("Closing agent listening on {}", ss.getLocalPort());
		Thread.interrupted();
		ss.close();
	}
}
