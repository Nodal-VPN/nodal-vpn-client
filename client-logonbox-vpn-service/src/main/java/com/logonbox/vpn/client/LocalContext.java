package com.logonbox.vpn.client;

import java.io.Closeable;
import java.net.CookieStore;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.messages.Message;
import org.slf4j.event.Level;

import com.logonbox.vpn.client.service.ClientService;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public interface LocalContext extends Closeable {

	PlatformService<?> getPlatformService();
	
	ClientService getClientService();

	void sendMessage(Message message);
	
	VPNFrontEnd registerFrontEnd(String source);
	
	VPNFrontEnd getFrontEnd(String source);

	boolean hasFrontEnd(String source);

	void deregisterFrontEnd(String source);
	
	boolean isRegistrationRequired();

	AbstractConnection getConnection();

	Collection<VPNFrontEnd> getFrontEnds();

	void shutdown(boolean restart);
	SSLContext getSSLContext();

	PromptingCertManager getCertManager();

	SSLParameters getSSLParameters();

	CookieStore getCookieStore();

	ScheduledExecutorService getQueue();
	
	Level getDefaultLevel();
	
	void setLevel(Level level);

	VPN getVPN();
	
	@Override
	void close();
	
}
