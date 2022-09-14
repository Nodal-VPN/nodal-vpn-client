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
	}
	 
	protected UpdateService createUpdateService() {
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
