package com.logonbox.vpn.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.Keys;

public abstract class AbstractPlatformService<I extends VirtualInetAddress<?>> implements PlatformService<I> {
	final static Logger LOG = LoggerFactory.getLogger(AbstractPlatformService.class);
	protected static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("logonbox.vpn.maxInterfaces", "250"));


	protected LocalContext context;
	private final String interfacePrefix;
	
	protected AbstractPlatformService(String interfacePrefix) {
		this.interfacePrefix = interfacePrefix;
	}
	
	protected void beforeStart(LocalContext ctx) {
	}
	
	protected Collection<VPNSession> onStart(LocalContext ctx, List<VPNSession> sessions) {
		return sessions;
	}

	@Override
    public void openToEveryone(Path path) throws IOException {
        LOG.info(String.format("Setting permissions on %s to %s", path,
                Arrays.asList(PosixFilePermission.values())));
        Files.setPosixFilePermissions(path, new LinkedHashSet<>(Arrays.asList(PosixFilePermission.values())));
    }

    @Override
    public void restrictToUser(Path path) throws IOException {
        var prms = Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        LOG.info(String.format("Setting permissions on %s to %s", path, prms));
        Files.setPosixFilePermissions(path, new LinkedHashSet<>(
                prms));
    }

    @Override
	public String[] getMissingPackages() {
		return new String[0];
	}

	@Override
	public final String pubkey(String privateKey) {
		return Keys.pubkey(privateKey).getBase64PublicKey();
	}
	
	@Override
	public final Collection<VPNSession> start(LocalContext context) {
		LOG.info(String.format("Starting platform services %s", getClass().getName()));
		this.context = context;
		beforeStart(context);

		/*
		 * Look for wireguard already existing interfaces, checking if they are
		 * connected. When we find some, find the associated Peer Configuration /
		 * Connection objects so we can populate the in-memory map of active sessions.
		 */
		LOG.info("Looking for existing wireguard interfaces.");
		List<VPNSession> sessions = new ArrayList<>();
		List<I> ips = ips(true);
		for (int i = 0; i < MAX_INTERFACES; i++) {
			String name = getInterfacePrefix() + i;
			if (exists(name, ips)) {
				LOG.info(String.format("Checking %s.", name));
				
				/*
				 * Interface exists, Find it's public key so we can match a peer configuration
				 */
				try {
					String publicKey = getPublicKey(name);
					if (publicKey != null) {
						LOG.info(String.format("%s has public key of %s.", name, publicKey));
						try {
							ConnectionStatus status = context.getClientService().getStatusForPublicKey(publicKey);
							Connection connection = status.getConnection();
							LOG.info(String.format(
									"Existing wireguard session on %s for %s, adding back to internal list", name, publicKey));
							I ip = get(name);
							sessions.add(configureExistingSession(context, connection, ip));
						}
						catch(Exception e) {
							LOG.info(String.format(
									"No known public key of %s on %s, so likely managed outside of LogonBox VPN.",
									publicKey, name));
						}
					} else {
						LOG.info(
								String.format("%s has no public key, so it likely used by another application.", name));
					}
				} catch (Exception e) {
					LOG.error("Failed to get peer configuration for existing wireguard interface.", e);
				}
			}
		}
		return onStart(context, sessions);
	}

	protected VPNSession configureExistingSession(LocalContext context, Connection connection, I ip) {
		return new VPNSession(connection, context, ip);
	}
	
	protected String getPublicKey(String interfaceName) throws IOException {
		throw new UnsupportedOperationException("Failed to get public key for " + interfaceName);
	}

	protected String getInterfacePrefix() {
		return System.getProperty("logonbox.vpn.interfacePrefix", interfacePrefix);
	}

	protected final boolean exists(String name, boolean wireguardOnly) {
		return exists(name, ips(wireguardOnly));
	}

	protected boolean exists(String name, Iterable<I> links) {
		try {
			find(name, links);
			return true;
		} catch (IllegalArgumentException iae) {
			return false;
		}
	}

	protected I find(String name, Iterable<I> links) {
		for (I link : links)
			if (Objects.equals(name, link.getName()))
				return link;
		throw new IllegalArgumentException(String.format("No IP item %s", name));
	}

	protected final I get(String name) {
		return find(name, ips(false));
	}
}
