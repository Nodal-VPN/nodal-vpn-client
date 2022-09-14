package com.logonbox.vpn.client;

import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.dbus.VPN;

public class ServicePromptingCertManager extends PromptingCertManager {

	private Main context;

	private Semaphore semaphore;

	public ServicePromptingCertManager(ResourceBundle bundle, Main context) {
		super(bundle);
		this.context = context;
	}

	static Logger log = LoggerFactory.getLogger(ServicePromptingCertManager.class);

	static Set<String> ACCEPTED_CERTIFICATES = new HashSet<>();
	static Preferences PERMANENTLY_ACCEPTED_CERTIFICATES = Preferences.userNodeForPackage(PromptingCertManager.class)
			.node("certificates");

	@Override
	protected boolean isToolkitThread() {
		return false;
	}

	@Override
	protected void runOnToolkitThread(Runnable r) {
		r.run();
	}

	@Override
	public boolean promptForCertificate(PromptType alertType, String title, String content, String key,
			String hostname, String message) {
		
		if(semaphore != null)
			throw new IllegalStateException("Already prompting for a certificate to be accepted.");
		
		if (context.getFrontEnds().isEmpty())
			throw new IllegalStateException(
					"Cannot prompt to accept invalid certificate. A front-end must be running.");
		
		semaphore = new Semaphore(1);
		try {
			semaphore.acquire();
		} catch (InterruptedException e1) {
			throw new IllegalStateException("Impossible!");
		}
		
		try {
			context.getConnection().sendMessage(new VPN.CertificatePrompt(context.getVPN().getObjectPath(),
					alertType.name(), title, content, key, hostname, message));
		} catch (DBusException e) {
			throw new IllegalStateException(
					"Cannot prompt to accept invalid certificate. Front-end running, but message could not be sent.",
					e);
		}

		try {
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
