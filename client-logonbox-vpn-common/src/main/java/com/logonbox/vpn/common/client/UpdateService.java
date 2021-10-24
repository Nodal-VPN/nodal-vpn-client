package com.logonbox.vpn.common.client;

import java.io.IOException;

public interface UpdateService {
	
	public interface Listener {
		void stateChanged();
	}
	
	void addListener(Listener listener);
	
	void removeListener(Listener listener);

	boolean isNeedsUpdating();

	boolean isUpdating();

	String[] getPhases();

	String getAvailableVersion();

	void deferUpdate();

	boolean isUpdatesEnabled();

	void checkForUpdate() throws IOException;

	void update() throws IOException;
	
	void shutdown();

	void checkIfBusAvailable();
}
