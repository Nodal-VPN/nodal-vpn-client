package com.logonbox.vpn.client.common;

import java.util.ResourceBundle;

public abstract class ClientPromptingCertManager extends PromptingCertManager {
	

	private AppContext<?> context;

    public ClientPromptingCertManager(ResourceBundle bundle, AppContext<?> context) {
		super(bundle);
		this.context = context;
	}

	@Override
	public boolean isAccepted(String encodedKey) {
        return context.getVpnManager().getVpnOrFail().isCertAccepted(encodedKey);
	}

	@Override
	public void accept(String encodedKey) {
	    context.getVpnManager().getVpnOrFail().acceptCert(encodedKey);
	}

	@Override
	public void reject(String encodedKey) {
	    context.getVpnManager().getVpnOrFail().rejectCert(encodedKey);
	}

	@Override
	public void save(String encodedKey) {
	    context.getVpnManager().getVpnOrFail().saveCert(encodedKey);
	}

}
