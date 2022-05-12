package com.logonbox.vpn.common.client;

import java.util.ResourceBundle;

import com.logonbox.vpn.common.client.dbus.DBusClient;

public abstract class ClientPromptingCertManager extends PromptingCertManager {
	
	private DBusClient dbusClient;

	public ClientPromptingCertManager(ResourceBundle bundle, DBusClient dbusClient) {
		super(bundle);
		this.dbusClient = dbusClient;
	}

	@Override
	public boolean isAccepted(String encodedKey) {
		return dbusClient.getVPN().isCertAccepted(encodedKey);
	}

	@Override
	public void accept(String encodedKey) {
		dbusClient.getVPN().acceptCert(encodedKey);
	}

	@Override
	public void reject(String encodedKey) {
		dbusClient.getVPN().rejectCert(encodedKey);
	}

	@Override
	public void save(String encodedKey) {
		dbusClient.getVPN().saveCert(encodedKey);
	}

}
