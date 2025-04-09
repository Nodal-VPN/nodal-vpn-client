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

import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;

@SuppressWarnings("serial")
public final class ClientTrustProvider extends Provider {
	public static final String TRUST_PROVIDER_ALG = "ClientTrustAlgorithm";
	private static final String TRUST_PROVIDER_ID = "ClientTrustProvider";
	private static TrustManager trustManager;

	public ClientTrustProvider(TrustManager trustManager) {
		super(TRUST_PROVIDER_ID, "0.1", "Delegates to UI.");
		Security.setProperty("ssl.TrustManagerFactory.algorithm", ClientTrustProvider.TRUST_PROVIDER_ALG);
		AccessController.doPrivileged(new PrivilegedAction<Void>() {
			@Override
			public Void run() {
				put("TrustManagerFactory." + ClientTrustManagerFactory.getAlgorithm(),
						ClientTrustManagerFactory.class.getName());
				return null;
			}
		});
		ClientTrustProvider.trustManager = trustManager;
	}

	public final static class ClientTrustManagerFactory extends TrustManagerFactorySpi {
		public ClientTrustManagerFactory() {
		}

		@Override
		protected void engineInit(ManagerFactoryParameters mgrparams) {
		}

		@Override
		protected void engineInit(KeyStore keystore) {
		}

		@Override
		protected TrustManager[] engineGetTrustManagers() {
			return new TrustManager[] { trustManager };
		}

		public static String getAlgorithm() {
			return TRUST_PROVIDER_ALG;
		}
	}
}
