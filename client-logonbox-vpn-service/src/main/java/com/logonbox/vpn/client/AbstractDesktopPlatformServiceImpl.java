package com.logonbox.vpn.client;

import com.github.jgonian.ipmath.AbstractIp;
import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6;
import com.github.jgonian.ipmath.Ipv6Range;
import com.logonbox.vpn.client.service.ClientService;
import com.logonbox.vpn.client.service.ReauthorizeException;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.client.wireguard.IpUtil;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.ConfigurationItem;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.logonbox.vpn.common.client.StatusDetail;
import com.logonbox.vpn.common.client.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractDesktopPlatformServiceImpl<I extends VirtualInetAddress<?>> extends AbstractPlatformService<I> {

	final static Logger LOG = LoggerFactory.getLogger(AbstractDesktopPlatformServiceImpl.class);
	
	protected Path tempCommandDir;
	
	protected AbstractDesktopPlatformServiceImpl(String interfacePrefix) {
		super(interfacePrefix);
	}

	protected Path extractCommand(String platform, String arch, String name) throws IOException {
		LOG.info(String.format("Extracting command %s for platform %s on arch %s", name, platform, arch));
		try(InputStream in = getClass().getResource("/" + platform + "-" + arch + "/" + name).openStream()) {
			Path path = getTempCommandDir().resolve(name);
			try(OutputStream out = Files.newOutputStream(path)) {
				in.transferTo(out);
			}
			path.toFile().deleteOnExit();
			Files.setPosixFilePermissions(path, new LinkedHashSet<>(Arrays.asList(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
			LOG.info(String.format("Extracted command %s for platform %s on arch %s to %s", name, platform, arch, path));
			return path;
		}
	}

	protected Path getTempCommandDir() throws IOException {
		if(tempCommandDir == null)
			tempCommandDir = Files.createTempDirectory("vpn");
		return tempCommandDir;
	}

	@Override
	public final I connect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		I vpn = onConnect(logonBoxVPNSession, configuration);
		if(configuration.isRouteAll()) {
			try {
				addRouteAll(configuration);
			}
			catch(Exception e) { 
				LOG.error("Failed to setup routing.", e);
			}
		}
		return vpn;
	}

	@SuppressWarnings("unchecked")
	@Override
	public final void disconnect(VPNSession session) throws IOException {
		doDisconnect((I) session.getIp(), session);
	}

	protected void doDisconnect(I ip, VPNSession session) throws IOException {
		try {
			try {
				ip.down();
			}
			finally {
				ip.delete();
			}
		}
		finally {
			if(session.getConnection().isRouteAll()) {
				try {
					removeRouteAll(session);
				}
				catch(Exception e) { 
					LOG.error("Failed to tear down routing.", e);
				}
			}
			onDisconnect();
		}
	}

	@Override
	public I getByPublicKey(String publicKey) {
		try {
			for (I ip : ips(true)) {
				if (publicKey.equals(getPublicKey(ip.getName()))) {
					return ip;
				}
			}
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed to list interface names.", ioe);
		}
		return null;
	}

	@Override
	public List<I> ips(boolean wireguardInterface) {
		List<I> ips = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> nifEn = NetworkInterface.getNetworkInterfaces(); nifEn
					.hasMoreElements();) {
				NetworkInterface nif = nifEn.nextElement();
				if ((wireguardInterface && isWireGuardInterface(nif)) || (!wireguardInterface && isMatchesPrefix(nif))) {
					I vaddr = createVirtualInetAddress(nif);
					configureVirtualAddress(vaddr);
					if (vaddr != null)
						ips.add(vaddr);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to get interfaces.", e);
		}
		return ips;
	}

	protected void configureVirtualAddress(I vaddr) {
		try {
			vaddr.method(context.getClientService().getValue(null, ConfigurationItem.DNS_INTEGRATION_METHOD));
		}
		catch(Exception e) {
			LOG.error("Failed to set DNS integeration method, reverting to AUTO.", e);
			vaddr.method(DNSIntegrationMethod.AUTO);
		}
	}

	@Override
	public boolean isAlive(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		long lastHandshake = getLatestHandshake(logonBoxVPNSession.getIp().getName(), configuration.getPublicKey());
		long now = System.currentTimeMillis();
		boolean alive = lastHandshake >= now - ( ClientService.HANDSHAKE_TIMEOUT * 1000);
		if(LOG.isDebugEnabled()) {
			LOG.debug("Checking if {} ({}) is still alive. Now is {}, Latest Handshake is {}, Timeout secs is {}, Alive is {}", 
					logonBoxVPNSession.getIp().getName(), configuration.getPublicKey(), now,
					lastHandshake, ClientService.HANDSHAKE_TIMEOUT, alive);
		}
		return alive;
	}

	@Override
	public StatusDetail status(String iface) throws IOException {
		return createPipe(iface);
	}
	
	protected WireguardPipe createPipe(String iface) throws IOException {
	    throw new UnsupportedOperationException();
	}

	protected abstract void addRouteAll(Connection connection) throws IOException;

	protected abstract I createVirtualInetAddress(NetworkInterface nif) throws IOException;

	protected void dns(Connection configuration, I ip) throws IOException {
		if(configuration.getDns().isEmpty()) {
			if(configuration.isRouteAll())
				LOG.warn("No DNS servers configured for this connection and all traffic is being routed through the VPN. DNS is unlikely to work.");
			else  
				LOG.info("No DNS servers configured for this connection.");
		}
		else {
			LOG.info(String.format("Configuring DNS servers for %s as %s", ip.getName(), configuration.getDns()));
		}
		ip.dns(configuration.getDns().toArray(new String[0]));
		
	}

	protected abstract String getDefaultGateway() throws IOException;

	protected String getPublicKey(String interfaceName) throws IOException {
		try {
			return createPipe(interfaceName).getUserPublicKey();
		}
		catch(FileNotFoundException fnfe) {
			return null;
		}
	}

	protected long getLatestHandshake(String iface, String publicKey) throws IOException {
		WireguardPipe pipe = createPipe(iface);
		return Objects.equals(publicKey, pipe.getPublicKey()) ? pipe.getLastHandshake() : 0;
	}
	
	public String getWGCommand() {
		return "wg";
	}

	protected boolean isMatchesPrefix(NetworkInterface nif) {
		return nif.getName().startsWith(getInterfacePrefix());
	}
	
	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return isMatchesPrefix(nif);
	}

	protected abstract I onConnect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException;

	protected void onDisconnect() throws IOException {
	}

	protected abstract void removeRouteAll(VPNSession session) throws IOException;
	
	protected I waitForFirstHandshake(Connection configuration, I ip, long connectionStarted)
			throws IOException {
		for(int i = 0 ; i < ClientService.CONNECT_TIMEOUT ; i++) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new IOException(String.format("Interrupted connecting to %s", ip.getName()));
			}
			try {
				long lastHandshake = getLatestHandshake(ip.getName(), configuration.getPublicKey());
				if(lastHandshake >= connectionStarted) {
					/* Ready ! */
					return ip;
				}
			}
			catch(RuntimeException iae) {
				try {
					ip.down();
				}
				catch(Exception e) {
					LOG.error("Failed to stop after error.", e);
				}
				throw iae;
			}
		}

		/* Failed to connect in the given time. Clean up and report an exception */
		try {
			ip.down();
		}
		catch(Exception e) {
			LOG.error("Failed to stop after timeout.", e);
		}
		var endpointName = configuration.getEndpointAddress();
		try {
			endpointName = InetAddress.getByName(endpointName).getHostName();
		}
		catch(Exception e) {
		}
		throw new ReauthorizeException(String.format("No handshake received from %s (%s) for %s within %d seconds.", configuration.getEndpointAddress(), endpointName, ip.getName(), ClientService.CONNECT_TIMEOUT));
	}
	
	protected void write(Connection configuration, Writer writer) {
		PrintWriter pw = new PrintWriter(writer, true);
		pw.println("[Interface]");
		pw.println(String.format("PrivateKey = %s", configuration.getUserPrivateKey()));
		writeInterface(configuration, writer);
		pw.println();
		pw.println("[Peer]");
		pw.println(String.format("PublicKey = %s", configuration.getPublicKey()));
		pw.println(
				String.format("Endpoint = %s:%d", configuration.getEndpointAddress(), configuration.getEndpointPort()));
		if (configuration.getPersistentKeepalive() > 0)
			pw.println(String.format("PersistentKeepalive = %d", configuration.getPersistentKeepalive()));
		List<String> allowedIps = new ArrayList<>(configuration.getAllowedIps());
		if(configuration.isRouteAll()) {
			pw.println("AllowedIPs = 0.0.0.0/0");
		}	
		else {
			if(context.getClientService().getValue(null, ConfigurationItem.IGNORE_LOCAL_ROUTES)) {
				/* Filter out any routes that would cover the addresses of any interfaces
				 * we already have
				 */
				Set<AbstractIp<?, ?>> localAddresses = new HashSet<>();
				try {
					for(Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
						NetworkInterface ni = en.nextElement();
						if(!ni.isLoopback() && ni.isUp()) { 
							for(Enumeration<InetAddress> addrEn = ni.getInetAddresses(); addrEn.hasMoreElements(); ) {
								InetAddress addr = addrEn.nextElement();
								try {
									localAddresses.add(IpUtil.parse(addr.getHostAddress()));
								}
								catch(IllegalArgumentException iae) {
									// Ignore
								}
							}
						}
					}
				}
				catch(SocketException se) {
					//
				}

				for(String route : new ArrayList<>(allowedIps)) {
					try {
						try {
							Ipv4Range range = Ipv4Range.parseCidr(route);
							for(AbstractIp<?, ?> laddr : localAddresses) {
								if(laddr instanceof Ipv4 && range.contains((Ipv4)laddr)) {
									// Covered by route. 
									LOG.info(String.format("Filtering out route %s as it covers an existing local interface address.", route));
									allowedIps.remove(route);
									break;
								}
							}
						}
						catch(IllegalArgumentException iae) {
							/* Single ipv4 address? */
							Ipv4 routeIpv4 = Ipv4.of(route);
							if(localAddresses.contains(routeIpv4)) {
								// Covered by route. 
								LOG.info(String.format("Filtering out route %s as it covers an existing local interface address.", route));
								allowedIps.remove(route);
								break;
							}
						}
					}
					catch(IllegalArgumentException iae) {
						try {
							Ipv6Range range = Ipv6Range.parseCidr(route);
							for(AbstractIp<?, ?> laddr : localAddresses) {
								if(laddr instanceof Ipv6 && range.contains((Ipv6)laddr)) {
									// Covered by route. 
									LOG.info(String.format("Filtering out route %s as it covers an existing local interface address.", route));
									allowedIps.remove(route);
									break;
								}
							}
						}
						catch(IllegalArgumentException iae2) {
							/* Single ipv6 address? */
							Ipv6 routeIpv6 = Ipv6.of(route);
							if(localAddresses.contains(routeIpv6)) {
								// Covered by route. 
								LOG.info(String.format("Filtering out route %s as it covers an existing local interface address.", route));
								allowedIps.remove(route);
								break;
							}
						}
					}
				}
			}
			
			String ignoreAddresses = System.getProperty("logonbox.vpn.ignoreAddresses", "");
			if(ignoreAddresses.length() > 0) {
				for(String ignoreAddress : ignoreAddresses.split(",")) {
					allowedIps.remove(ignoreAddress);
				}
			}
			if (!allowedIps.isEmpty())
				pw.println(String.format("AllowedIPs = %s", String.join(", ", allowedIps)));
		}
		writePeer(configuration, writer);
	}
	
	protected void writeInterface(Connection configuration, Writer writer) {
	}

	protected void writePeer(Connection configuration, Writer writer) {
	}

	protected void runHookViaPipeToShell(VPNSession session, String... args) throws IOException {
		if(LOG.isDebugEnabled()) {
			LOG.debug("Executing hook");
			for(String arg : args) {
				LOG.debug(String.format("    %s", arg));
			}
		}
		Connection connection = session.getConnection();
		Map<String, String> env = new HashMap<String, String>();
		if(connection != null) {
			env.put("LBVPN_ADDRESS", connection.getAddress());
			env.put("LBVPN_DEFAULT_DISPLAY_NAME", connection.getDefaultDisplayName());
			env.put("LBVPN_DISPLAY_NAME", connection.getDisplayName());
			env.put("LBVPN_ENDPOINT_ADDRESS", connection.getEndpointAddress());
			env.put("LBVPN_ENDPOINT_PORT", String.valueOf(connection.getEndpointPort()));
			env.put("LBVPN_HOSTNAME", connection.getHostname());
			env.put("LBVPN_NAME", connection.getName() == null ? "": connection.getName());
			env.put("LBVPN_PEER_PUBLIC_KEY", connection.getPublicKey());
			env.put("LBVPN_USER_PUBLIC_KEY", connection.getUserPublicKey());
			env.put("LBVPN_ID", String.valueOf(connection.getId()));
			env.put("LBVPN_PORT", String.valueOf(connection.getPort()));
			env.put("LBVPN_DNS", String.join(" ", connection.getDns()));
			env.put("LBVPN_MTU", String.valueOf(connection.getMtu()));
		}
		@SuppressWarnings("unchecked")
		I addr = (I)session.getIp();
		if(addr != null) {
			env.put("LBVPN_IP_MAC", addr.getMac());
			env.put("LBVPN_IP_NAME", addr.getName());
			env.put("LBVPN_IP_DISPLAY_NAME", addr.getDisplayName());
			env.put("LBVPN_IP_PEER", addr.getPeer());
			env.put("LBVPN_IP_TABLE", addr.getTable());
		}
		if(LOG.isDebugEnabled()) {
			LOG.debug("Environment:-");
			for(Map.Entry<String, String> en : env.entrySet()) {
				LOG.debug("    %s = %s", en.getKey(), en.getValue());
			}
		}
		Process p = startProcess(env, args);
		String errorMessage = null;
		BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line = null;
		LOG.debug("Command Output: ");
		while ((line = reader.readLine()) != null) {
			LOG.debug(String.format("    %s", line));
			if(line.startsWith("[ERROR] ")) {
				errorMessage = line.substring(8);
			}
		}
		try {
			int ret = p.waitFor();
			LOG.debug(String.format("Exit: %d", ret));
			if(ret != 0) {
				if(errorMessage == null)
					throw new IOException(String.format("Hook exited with non-zero status of %d.", ret));
				else
					throw new IOException(errorMessage);
			}
		} catch (InterruptedException e) {
			throw new IOException("Interrupted.", e);
		}
		
	}

	@Override
	public void runHook(VPNSession session, String hookScript) throws IOException {
		for(String cmd : split(hookScript)) {
		    runCommand(Util.parseQuotedString(cmd));
		}
	}
	
	protected abstract Process startProcess(Map<String, String> env, String...args) throws IOException;
	
	protected abstract void runCommand(List<String> commands) throws IOException;
	
	private Collection<? extends String> split(String str) {
		str = str == null ? "" : str.trim();
		return str.equals("") ? Collections.emptyList() : Arrays.asList(str.split("\n"));
	}
}
