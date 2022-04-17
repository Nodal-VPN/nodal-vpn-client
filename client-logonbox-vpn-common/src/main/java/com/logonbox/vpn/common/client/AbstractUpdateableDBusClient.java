package com.logonbox.vpn.common.client;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(classes = true, fields = true, methods = true)
public abstract class AbstractUpdateableDBusClient extends AbstractDBusClient {

	/**
	 * Matches the identifier in logonbox VPN server
	 * PeerConfigurationAuthenticationProvider.java
	 */
	public static final String DEVICE_IDENTIFIER = "LBVPNDID";
	
	private UpdateService updateService;

	protected AbstractUpdateableDBusClient(String defaultLogFilePattern) {
		super(defaultLogFilePattern);
		try {
			updateService = new Install4JUpdateServiceImpl(this);
		} catch (Throwable t) {
			updateService = new DummyUpdateService(this);
		}
	}

	public UpdateService getUpdateService() {
		return updateService;
	}

	public void exit() {
		updateService.shutdown();
		super.exit();
	}

	@Override
	protected final void onBusGone() {
		updateService.checkIfBusAvailable();
	}

	@Override
	protected void onInit() {
		if (getLog().isDebugEnabled())
			getLog().debug(String.format("Using update service %s", updateService.getClass().getName()));
		updateService.checkIfBusAvailable();
	}

}
