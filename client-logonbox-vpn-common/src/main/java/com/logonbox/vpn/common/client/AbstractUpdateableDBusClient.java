package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.SystemUtils;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

import com.sun.jna.platform.win32.Advapi32Util;

@Reflectable
@TypeReflect(classes = true, fields = true, methods = true)
public abstract class AbstractUpdateableDBusClient extends AbstractDBusClient {

	
	public final static String SID_ADMINISTRATORS_GROUP = "S-1-5-32-544";
	/**
	 * Matches the identifier in logonbox VPN server
	 * PeerConfigurationAuthenticationProvider.java
	 */
	public static final String DEVICE_IDENTIFIER = "LBVPNDID";
	
	private UpdateService updateService;

	protected AbstractUpdateableDBusClient(String defaultLogFilePattern) {
		super(defaultLogFilePattern);
	}
	 
	protected UpdateService createUpdateService() {
		if(isElevatableToAdministrator()) {
			try {
				 return new Install4JUpdateServiceImpl(this);
			} catch (Throwable t) {
				if(getLog().isDebugEnabled())
					getLog().info("Failed to create Install4J update service, using dummy service.", t);
				else
					getLog().info("Failed to create Install4J update service, using dummy service. {}", t.getMessage());
				return new DummyUpdateService(this);
			}
		}
		else {
			return new NoUpdateService(this);
		}
	}
	
	boolean isElevatableToAdministrator() {
		if(SystemUtils.IS_OS_WINDOWS) {
			for(var grp : Advapi32Util.getCurrentUserGroups()) {
				if(SID_ADMINISTRATORS_GROUP.equals(grp.sidString)) {
					return true;
				}
			}
			return false;
		}
		else if(SystemUtils.IS_OS_LINUX) {
			try {
				return Arrays.asList(String.join(" ", Util.runCommandAndCaptureOutput("id", "-Gn")).split("\\s+")).contains("sudo");
			} catch (IOException e) {
				getLog().error("Failed to test for user groups, assuming can elevate.", e);
			}
		}
		else if(SystemUtils.IS_OS_MAC_OSX) {
			try {
				return Arrays.asList(String.join(" ", Util.runCommandAndCaptureOutput("id", "-Gn")).split("\\s+")).contains("admin");
			} catch (IOException e) {
				getLog().error("Failed to test for user groups, assuming can elevate.", e);
			}
		}
		return true;
	}

	public UpdateService getUpdateService() {
		if(updateService == null)
			updateService = createUpdateService();
		return updateService;
	}

	public void exit() {
		getUpdateService().shutdown();
		super.exit();
	}

	@Override
	protected final void onBusGone() {
		getUpdateService().checkIfBusAvailable();
	}

	@Override
	protected void onInit() {
		if (getLog().isDebugEnabled())
			getLog().debug(String.format("Using update service %s", updateService.getClass().getName()));
		getUpdateService().checkIfBusAvailable();
	}

}
