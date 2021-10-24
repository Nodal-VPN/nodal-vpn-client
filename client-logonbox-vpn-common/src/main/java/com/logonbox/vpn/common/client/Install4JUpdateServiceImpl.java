package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URL;

import org.apache.commons.io.output.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		String configuredPhase = context.getVPN().getValue("phase", "stable");
		return "https://logonbox-packages.s3.eu-west-1.amazonaws.com/logonbox-vpn-client/" + configuredPhase
				+ "/updates.xml";
	}

	@Override
	protected String doUpdate(boolean checkOnly) throws IOException {
		
		/* Ping the Hypersocket extension store. This is only used for statistics, updates 
		 * are calculated and obtained directly from S3.
		 * 
		 * Do this in the background
		 */
		scheduler.execute(() -> {
			try {
				// https://updates2.hypersocket.com/hypersocket/api/store/repos2/2.4.0-456/logonbox-vpn-client/265f97ff-3d65-454d-aadc-a427385b20e2/CLIENT_SERVICE
				URL url = new URL(String.format("%s/api/store/repos2/%s/logonbox-vpn-client/%s/CLIENT_SERVICE", 
						System.getProperty("hypersocket.archivesURL", "https://updates2.hypersocket.com/hypersocket"),
						context.getVersion(),
						HypersocketVersion.getSerial()));
				try(InputStream in = url.openStream()) {
					in.transferTo(new NullOutputStream());
				}
				log.debug("Pinged " + url);
			}
			catch(Exception e) {
				if(log.isDebugEnabled()) {
					log.error("Failed to ping extension store.", e);
				}
			}
		});
		
		 
		
		String uurl = buildUpdateUrl();
		log.info("Check for updates in " + context.getVersion() + " from " + uurl);
		UpdateDescriptor update;
		try {
			update = UpdateChecker.getUpdateDescriptor(uurl, context.isConsole() ? ApplicationDisplayMode.CONSOLE : ApplicationDisplayMode.GUI);
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
			}
			else {
				if (!isNeedsUpdating())
					throw new IOException("Update not needed.");
				ApplicationLauncher.launchApplicationInProcess("2103",
						new String[] { /* "-q" */ }, new ApplicationLauncher.Callback() {
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

}
