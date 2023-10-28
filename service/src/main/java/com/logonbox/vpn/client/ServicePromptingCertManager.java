package com.logonbox.vpn.client;

import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;

public class ServicePromptingCertManager<CONX extends IVpnConnection> extends PromptingCertManager {

	private LocalContext<CONX> context;

	private Semaphore semaphore;

	public ServicePromptingCertManager(ResourceBundle bundle, LocalContext<CONX> context) {
		super(bundle);
		this.context = context;
	}

	static Logger log = LoggerFactory.getLogger(ServicePromptingCertManager.class);

	static Set<String> ACCEPTED_CERTIFICATES = new HashSet<>();
	static Preferences PERMANENTLY_ACCEPTED_CERTIFICATES = Preferences.userNodeForPackage(PromptingCertManager.class)
			.node("certificates");

	@Override
	public boolean isToolkitThread() {
		return false;
	}

	@Override
	public void runOnToolkitThread(Runnable r) {
		r.run();
	}

	@Override
	public boolean promptForCertificate(PromptType alertType, String title, String content, String key,
			String hostname, String message) {
		
		if(semaphore != null)
			throw new IllegalStateException("Already prompting for a certificate to be accepted.");
		
		
		semaphore = new Semaphore(1);
		try {
			semaphore.acquire();
		} catch (InterruptedException e1) {
			throw new IllegalStateException("Impossible!");
		}
		

		try {
		    context.promptForCertificate(alertType, title, content, key, hostname, message);
			semaphore.acquire();
			semaphore.release();
		} catch (InterruptedException e1) {
			throw new IllegalStateException("Interrupted awaiting prompt reply.");
		} finally {
			semaphore = null;
		}
		
		return true;
	}

	public boolean isAccepted(String encodedKey) {
		return ACCEPTED_CERTIFICATES.contains(encodedKey)
				|| PERMANENTLY_ACCEPTED_CERTIFICATES.getBoolean(encodedKey, false);
	}

	@Override
	public void accept(String encodedKey) {
		ACCEPTED_CERTIFICATES.add(encodedKey);
		if(semaphore != null)
			semaphore.release();
	}

	@Override
	public void save(String encodedKey) {
		PERMANENTLY_ACCEPTED_CERTIFICATES.putBoolean(encodedKey, true);

	}

	@Override
	public void reject(String encodedKey) {
		ACCEPTED_CERTIFICATES.remove(encodedKey);
		PERMANENTLY_ACCEPTED_CERTIFICATES.remove(encodedKey);
		if(semaphore != null)
			semaphore.release();
	}

}
