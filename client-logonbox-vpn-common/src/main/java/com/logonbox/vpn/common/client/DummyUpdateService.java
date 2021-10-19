package com.logonbox.vpn.common.client;

import java.io.IOException;

public class DummyUpdateService implements UpdateService {

	@Override
	public boolean isNeedsUpdating() {
		return false;
	}

	@Override
	public boolean isUpdating() {
		return false;
	}

	@Override
	public String[] getPhases() {
		return new String[] { "stable", "ea", "nightly"};
	}

	@Override
	public String getAvailableVersion() {
		return "9.9.9";
	}

	@Override
	public void deferUpdate() {
	}

	@Override
	public boolean isUpdatesEnabled() {
		return true;
	}

	@Override
	public void checkForUpdate() throws IOException {
	}

	@Override
	public void update() throws IOException {
	}

}
