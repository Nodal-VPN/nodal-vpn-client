package com.logonbox.vpn.client.common;

import java.util.ResourceBundle;

public abstract class ClientPromptingCertManager extends PromptingCertManager {
	
	private final VpnManager<?> context;

	public ClientPromptingCertManager(ResourceBundle bundle, VpnManager<?> dbusClient) {
		super(bundle);
		this.context = dbusClient;
	}

	@Override
	public boolean isAccepted(String encodedKey) {
		return context.getVPNOrFail().isCertAccepted(encodedKey);
	}

	@Override
	public void accept(String encodedKey) {
		context.getVPNOrFail().acceptCert(encodedKey);
	}

	@Override
	public void reject(String encodedKey) {
		context.getVPNOrFail().rejectCert(encodedKey);
	}

	@Override
	public void save(String encodedKey) {
		context.getVPNOrFail().saveCert(encodedKey);
	}

}
