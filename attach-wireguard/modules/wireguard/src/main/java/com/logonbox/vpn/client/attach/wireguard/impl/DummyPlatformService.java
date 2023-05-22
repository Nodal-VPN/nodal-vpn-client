package com.logonbox.vpn.client.attach.wireguard.impl;

import com.logonbox.vpn.client.AbstractPlatformService;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.logonbox.vpn.common.client.StatusDetail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Dummy implementation of a {@link PlatformService}, responsible for querying
 * the state of existing VPN connections, creating new ones and other platform
 * specific actions.
 */
public class DummyPlatformService extends AbstractPlatformService<AndroidIP> {

    /**
     * Constructor
     */
	protected DummyPlatformService() {
		super("wg");
	}

	@Override
	public void openToEveryone(Path path) throws IOException {
	}

	@Override
	public void restrictToUser(Path path) throws IOException {
	}

	@Override
	public AndroidIP connect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public AndroidIP getByPublicKey(String publicKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAlive(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void disconnect(VPNSession session) throws IOException {
		throw new UnsupportedOperationException();		
	}

	@Override
	protected String getPublicKey(String interfaceName) throws IOException {
		// TODO Auto-generated method stub
		return super.getPublicKey(interfaceName);
	}

	@Override
	public List<AndroidIP> ips(boolean wireguardOnly) {
		/* TODO */
		return Collections.emptyList();
	}

	@Override
	public StatusDetail status(String interfaceName) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void runHook(VPNSession session, String hookScript) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public DNSIntegrationMethod dnsMethod() {
		/* TODO */
		return DNSIntegrationMethod.NONE;
	}

}
