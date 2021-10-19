package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		String configuredPhase = context.getVPN().getValue("phase", "");
		return "https://logonbox-packages.s3.eu-west-1.amazonaws.com/logonbox-vpn-client/" + configuredPhase
				+ "/updates.xml";
	}

	@Override
	protected String doUpdate(boolean checkOnly) throws IOException {
		String uurl = buildUpdateUrl();
		log.info("Check for updates in " + context.getVersion() + " from " + uurl);
		UpdateDescriptor update;
		try {
			update = UpdateChecker.getUpdateDescriptor(uurl, ApplicationDisplayMode.GUI);
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
				ApplicationLauncher.launchApplicationInProcess("2103", null, new ApplicationLauncher.Callback() {
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
