package com.logonbox.vpn.client.tray;

import java.util.ResourceBundle;

import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Bundle
@Resource(siblings = true)
public interface Tray extends AutoCloseable {

	final static ResourceBundle bundle = ResourceBundle.getBundle(Tray.class.getName());
	
	boolean isActive();
	
	void reload();
	
	default boolean isConfigurable() {
		return true;
	}
	
	void loop() throws InterruptedException;
	
	void setProgress(int progress);

	void setAttention(boolean enabled, boolean critical);
}
