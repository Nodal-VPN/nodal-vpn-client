/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.logonbox.vpn.client.common;

import com.jadaptive.nodal.core.lib.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public abstract class PromptingCertManager implements X509TrustManager, HostnameVerifier {

	static Logger log = LoggerFactory.getLogger(PromptingCertManager.class);

	public static enum PromptType {
        NONE,
        INFORMATION,
        WARNING,
        CONFIRMATION,
        ERROR
    } 

	private ResourceBundle bundle;
	private SSLContext sslContext;
	private SSLParameters sslParameters;

	public PromptingCertManager(ResourceBundle bundle) {
		this.bundle = bundle;
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new UnsupportedOperationException();
	}

	protected boolean isStrictSSL() {
		return "true".equals(System.getProperty("logonbox.vpn.strictSSL", "true"));
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (!isStrictSSL()) {
			return;
		}

		List<String> chainSubjectDN = new ArrayList<>();
		for (X509Certificate c : chain) {
			try {
				if (log.isDebugEnabled())
					log.debug(String.format("Validating: %s", c));
				chainSubjectDN.add(c.getSubjectDN().toString());
				c.checkValidity();
			} catch (CertificateExpiredException | CertificateNotYetValidException ce) {
				/* Already been accepted? */
				String encodedKey = Util.hash(c.getPublicKey().getEncoded());
				if (isAccepted(encodedKey)) {
					log.debug(String.format("Accepting server certificate, it has previously been accepted."));
					return;
				}
				String title = bundle
						.getString(ce instanceof CertificateExpiredException ? "certificate.certificateExpired.title"
								: "certificate.certificateNotYetValid.title");
				String content = bundle
						.getString(ce instanceof CertificateExpiredException ? "certificate.certificateExpired.content"
								: "certificate.certificateNotYetValid.content");
				
				if (isToolkitThread()) {
					boolean ok = promptForCertificate(PromptType.WARNING, title, content, encodedKey,
							c.getSubjectDN().toString(), ce.getMessage());
					if (ok) {
						accept(encodedKey);
					} else
						reject(encodedKey);
						throw ce;
				} else {
					AtomicBoolean res = new AtomicBoolean();
					Semaphore sem = new Semaphore(1);
					try {
						sem.acquire();
						runOnToolkitThread(() -> {
							res.set(promptForCertificate(PromptType.WARNING, title, content, encodedKey,
									c.getSubjectDN().toString(), ce.getMessage()));
							sem.release();
						});
						sem.acquire();
						sem.release();
						boolean ok = res.get();
						if (ok) {
							accept(encodedKey);
						}
						return;
					} catch (InterruptedException ie) {
						throw ce;
					}
				}

			}
		}
	}

	public abstract boolean isAccepted(String encodedKey);

	public abstract void accept(String encodedKey);

	public abstract void reject(String encodedKey);

	public void installCertificateVerifier() {

		if (!isStrictSSL()) {
			log.warn(
					"NOT FOR PRODUCTION USE. All SSL certificates will be trusted regardless of status. This should only be used for testing.");
		}

		Security.insertProviderAt(new ClientTrustProvider(this), 1);
		Security.setProperty("ssl.TrustManagerFactory.algorithm", ClientTrustProvider.TRUST_PROVIDER_ALG);

		try {
			sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, new TrustManager[] { this }, new java.security.SecureRandom());
			sslParameters = sslContext.getDefaultSSLParameters();
			sslParameters.setEndpointIdentificationAlgorithm(null);
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
			SSLContext.setDefault(sslContext);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not initialise SSL.", e);
		}

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(this);
	}

	public SSLContext getSSLContext() {
		return sslContext;
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		X509Certificate[] NO_CERTS = new X509Certificate[0];
		return NO_CERTS;
	}
	
	@Override
	public synchronized boolean verify(String hostname, SSLSession session) {
		log.debug(String.format("Verify hostname %s: %s", hostname, session));
		if (!isStrictSSL())
			return true;

		/* Already been accepted? */
		String encodedKey;
		try {
			X509Certificate x509Certificate = (X509Certificate) session.getPeerCertificates()[0];
			encodedKey = Util.hash(x509Certificate.getPublicKey().getEncoded());
		} catch (SSLPeerUnverifiedException e) {
			throw new IllegalStateException("Failed to extract certificate.", e);
		}

		try {
			if (isAccepted(encodedKey)) {
				log.debug(String.format(
						"Accepting certificate for hostname %s, it has previously been accepted: %s", hostname,
						session));
				return true;
			}

			verifyHostname(session);
			return true;
		} catch (SSLPeerUnverifiedException sslpue) {
			if (isToolkitThread()) {
				boolean ok = promptForCertificate(PromptType.WARNING,
						bundle.getString("certificate.invalidCertificate.title"),
						bundle.getString("certificate.invalidCertificate.content"), encodedKey, hostname,
						sslpue.getMessage());
				if (ok) {
					accept(encodedKey);
				}
				else
					reject(encodedKey);
				return ok;
			} else {
				AtomicBoolean res = new AtomicBoolean();
				Semaphore sem = new Semaphore(1);
				try {
					sem.acquire();
					runOnToolkitThread(() -> {
						res.set(promptForCertificate(PromptType.WARNING,
								bundle.getString("certificate.invalidCertificate.title"),
								bundle.getString("certificate.invalidCertificate.content"), encodedKey,
								hostname, sslpue.getMessage()));
						sem.release();
					});
					sem.acquire();
					sem.release();
					boolean ok = res.get();
					if (ok) {
						accept(encodedKey);
					}
					return ok;
				} catch (InterruptedException ie) {
					return false;
				}
			}
		}
	}
	protected void verifyHostname(SSLSession sslSession) throws SSLPeerUnverifiedException {
		try {
			String hostname = sslSession.getPeerHost();
			X509Certificate serverCertificate = (X509Certificate) sslSession.getPeerCertificates()[0];

			Collection<List<?>> subjectAltNames = serverCertificate.getSubjectAlternativeNames();

			if (isIpv4Address(hostname)) {
				/*
				 * IP addresses are not handled as part of RFC 6125. We use the RFC 2818
				 * (Section 3.1) behaviour: we try to find it in an IP address Subject Alt.
				 * Name.
				 */
				for (List<?> sanItem : subjectAltNames) {
					/*
					 * Each item in the SAN collection is a 2-element list. See <a href=
					 * "http://docs.oracle.com/javase/7/docs/api/java/security/cert/X509Certificate.html#getSubjectAlternativeNames%28%29"
					 * >X509Certificate.getSubjectAlternativeNames()</a>. The first element in each
					 * list is a number indicating the type of entry. Type 7 is for IP addresses.
					 */
					if ((sanItem.size() == 2) && ((Integer) sanItem.get(0) == 7)
							&& (hostname.equalsIgnoreCase((String) sanItem.get(1)))) {
						return;
					}
				}
				throw new SSLPeerUnverifiedException(MessageFormat
						.format(bundle.getString("certificate.verify.error.noIpv4HostnameMatch"), hostname));
			} else {
				boolean anyDnsSan = false;
				for (List<?> sanItem : subjectAltNames) {
					/*
					 * Each item in the SAN collection is a 2-element list. See <a href=
					 * "http://docs.oracle.com/javase/7/docs/api/java/security/cert/X509Certificate.html#getSubjectAlternativeNames%28%29"
					 * >X509Certificate.getSubjectAlternativeNames()</a>. The first element in each
					 * list is a number indicating the type of entry. Type 2 is for DNS names.
					 */
					if ((sanItem.size() == 2) && ((Integer) sanItem.get(0) == 2)) {
						anyDnsSan = true;
						if (matchHostname(hostname, (String) sanItem.get(1))) {
							return;
						}
					}
				}

				/*
				 * If there were not any DNS Subject Alternative Name entries, we fall back on
				 * the Common Name in the Subject DN.
				 */
				if (!anyDnsSan) {
					String commonName = getCommonName(serverCertificate);
					if (commonName != null && matchHostname(hostname, commonName)) {
						return;
					}
				}

				throw new SSLPeerUnverifiedException(MessageFormat
						.format(bundle.getString("certificate.verify.error.noSanHostnameMatch"), hostname));
			}
		} catch (CertificateParsingException e) {
			/*
			 * It's quite likely this exception would have been thrown in the trust manager
			 * before this point anyway.
			 */

			throw new SSLPeerUnverifiedException(
					MessageFormat.format(bundle.getString("certificate.verify.error.failedToParse"), e.getMessage()));
		}
	}

	public boolean matchHostname(String hostname, String certificateName) {
		if (hostname.equalsIgnoreCase(certificateName)) {
			return true;
		}
		/*
		 * Looking for wildcards, only on the left-most label.
		 */
		String[] certificateNameLabels = certificateName.split(".");
		String[] hostnameLabels = certificateName.split(".");
		if (certificateNameLabels.length != hostnameLabels.length) {
			return false;
		}
		/*
		 * TODO: It could also be useful to check whether there is a minimum number of
		 * labels in the name, to protect against CAs that would issue wildcard
		 * certificates too loosely (e.g. *.com).
		 */
		/*
		 * We check that whatever is not in the first label matches exactly.
		 */
		for (int i = 1; i < certificateNameLabels.length; i++) {
			if (!hostnameLabels[i].equalsIgnoreCase(certificateNameLabels[i])) {
				return false;
			}
		}
		/*
		 * We allow for a wildcard in the first label.
		 */
		if (certificateNameLabels.length > 0 && "*".equals(certificateNameLabels[0])) {
			// TODO match wildcard that are only part of the label.
			return true;
		}
		return false;
	}

	public String getCommonName(X509Certificate cert) {
		try {
			LdapName ldapName = new LdapName(cert.getSubjectX500Principal().getName());
			/*
			 * Looking for the "most specific CN" (i.e. the last).
			 */
			String cn = null;
			for (Rdn rdn : ldapName.getRdns()) {
				if ("CN".equalsIgnoreCase(rdn.getType())) {
					cn = rdn.getValue().toString();
				}
			}
			return cn;
		} catch (InvalidNameException e) {
			return null;
		}
	}

	public boolean isIpv4Address(String hostname) {
		String[] ipSections = hostname.split("\\.");
		if (ipSections.length != 4) {
			return false;
		}
		for (String ipSection : ipSections) {
			try {
				int num = Integer.parseInt(ipSection);
				if (num < 0 || num > 255) {
					return false;
				}
			} catch (NumberFormatException e) {
				return false;
			}
		}
		return true;
	}

	public abstract boolean isToolkitThread();

	public abstract void runOnToolkitThread(Runnable r);

	public abstract boolean promptForCertificate(PromptType alertType, String title, String content, String key,
			String hostname, String message);
	
	public abstract void save(String encodedKey);

	public SSLParameters getSSLParameters() {
		return sslParameters;
	}
}
