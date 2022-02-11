package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.json.utils.HypersocketUtils;
import com.hypersocket.json.version.HypersocketVersion;
import com.install4j.api.context.UserCanceledException;
import com.install4j.api.launcher.ApplicationLauncher;
import com.install4j.api.update.ApplicationDisplayMode;
import com.install4j.api.update.UpdateChecker;
import com.install4j.api.update.UpdateDescriptor;
import com.install4j.api.update.UpdateDescriptorEntry;

public class Install4JUpdateServiceImpl extends AbstractUpdateService {

	static Logger log = LoggerFactory.getLogger(Install4JUpdateServiceImpl.class);

	public Install4JUpdateServiceImpl(AbstractDBusClient context) {
		super(context);
	}

	protected String buildUpdateUrl() {
		String configuredPhase = context.getVPN().getValue("phase");
		return "https://logonbox-packages.s3.eu-west-1.amazonaws.com/logonbox-vpn-client/" + configuredPhase
				+ "/updates.xml";
	}

	@Override
	protected String doUpdate(boolean checkOnly) throws IOException {

		/*
		 * Ping the Hypersocket extension store. This is only used for statistics,
		 * updates are calculated and obtained directly from S3.
		 * 
		 * Do this in the background
		 */
		scheduler.execute(() -> {
			try {
				resolveRemoteDependencies(Util.checkEndsWithSlash(getExtensionStoreRoot()) + "api/store/repos2",
						new String[] { "logonbox-vpn-client" }, context.getVersion(), HypersocketVersion.getSerial(),
						"VPN Client", getCustomerInfo(), "CLIENT_SERVICE");
			} catch (Exception e) {
				if (log.isDebugEnabled()) {
					log.error("Failed to ping extension store.", e);
				}
			}
		});

		String uurl = buildUpdateUrl();
		log.info("Check for updates in " + context.getVersion() + " from " + uurl);
		UpdateDescriptor update;
		try {
			update = UpdateChecker.getUpdateDescriptor(uurl,
					context.isConsole() ? ApplicationDisplayMode.CONSOLE : ApplicationDisplayMode.GUI);
			UpdateDescriptorEntry best = update.getPossibleUpdateEntry();
			if (best == null) {
				log.info("No version available.");
				return null;
			}

			String availableVersion = best.getNewVersion();
			log.info(availableVersion + " is available.");

			/* TODO: This will allow downgrades. */
			if (!availableVersion.equals(context.getVersion())) {
				log.info("Update available.");
			} else {
				log.info("No update needed.");
				return null;
			}

			if (checkOnly) {
				return availableVersion;
			} else {
				if (!isNeedsUpdating())
					throw new IOException("Update not needed.");
				ApplicationLauncher.launchApplicationInProcess("2103", new String[] { /* "-q" */ },
						new ApplicationLauncher.Callback() {
							public void exited(int exitValue) {
								context.exit();
							}

							public void prepareShutdown() {
								// TODO add your code here (not invoked on event dispatch thread)
							}
						}, ApplicationLauncher.WindowMode.FRAME, null);
			}
		} catch (UserCanceledException e) {
			log.info("Cancelled.");
			throw new InterruptedIOException("Cancelled.");
		} catch (Exception e) {
			log.info("Failed.", e);
		}
		return null;

	}

	private String getCustomerInfo() {
		try {
			if (context.getVPNConnections().isEmpty()) {
				return "New Install";
			} else {
				return context.getVPNConnections().iterator().next().getHostname();
			}
		} catch (Throwable t) {
			return "Default Install";
		}
	}

	static String getExtensionStoreRoot() {
		String url = System.getProperty("hypersocket.archivesURL", "https://updates2.hypersocket.com/hypersocket/");
		return url.endsWith("/") ? url : url + "/";
	}

	public static void resolveRemoteDependencies(String url, String[] repos, String version, String serial,
			String product, String customer, String... targets) throws IOException {

		HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
				.connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
		try {

			String additionalRepos = System.getProperty("hypersocket.privateRepos");
			if (additionalRepos != null) {
				if (log.isInfoEnabled()) {
					log.info(String.format("Adding private repos %s", additionalRepos));
				}
				repos = ArrayUtils.addAll(repos, additionalRepos.split(","));
			}
			if (StringUtils.isBlank(additionalRepos)) {
				additionalRepos = System.getProperty("hypersocket.additionalRepos");
				if (additionalRepos != null) {
					if (log.isInfoEnabled()) {
						log.info(String.format("Adding additional repos %s", additionalRepos));
					}
					repos = ArrayUtils.addAll(repos, additionalRepos.split(","));
				}
			}
			String updateUrl = String.format("%s/%s/%s/%s/%s", url, version, HypersocketUtils.csv(repos), serial,
					HypersocketUtils.csv(targets));

			if (log.isInfoEnabled()) {
				log.info("Checking for updates from " + updateUrl);
			}

			Map<String, String> params = new HashMap<String, String>();
			params.put("product", product);
			params.put("customer", customer);

			HttpRequest request = HttpRequest.newBuilder(new URI(updateUrl)).header("Accept", "application/json")
					.header("Content-Type", "application/x-www-form-urlencoded").POST(Util.ofMap(params)).build();

			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (log.isDebugEnabled()) {
				log.debug(HypersocketUtils.prettyPrintJson(response.body()));
			}

		} catch (Exception ex) {
			throw new IOException("Failed to resolve remote extensions. " + ex.getMessage(), ex);
		}
	}

}
