package com.logonbox.vpn.client.common;

import java.util.ResourceBundle;

public abstract class ClientPromptingCertManager extends PromptingCertManager {
	
    private final VpnManager<?> vpnManager;

	public ClientPromptingCertManager(ResourceBundle bundle, AppContext<?> context) {
		super(bundle);
        vpnManager = context.getVpnManager();
	}

	@Override
	public boolean isAccepted(String encodedKey) {
        return vpnManager.getVpnOrFail().isCertAccepted(encodedKey);
	}

	@Override
	public void accept(String encodedKey) {
	    vpnManager.getVpnOrFail().acceptCert(encodedKey);
	}

	@Override
	public void reject(String encodedKey) {
	    vpnManager.getVpnOrFail().rejectCert(encodedKey);
	}

	@Override
	public void save(String encodedKey) {
	    vpnManager.getVpnOrFail().saveCert(encodedKey);
	}

}
