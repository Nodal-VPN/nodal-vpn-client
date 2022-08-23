package com.logonbox.vpn.common.client;

import java.io.IOException;

public class NoUpdateService implements UpdateService {

	private AbstractDBusClient context;

	public NoUpdateService(AbstractDBusClient context) {
		this.context = context;
	}

	@Override
	public void addDownloadListener(DownloadListener listener) {
	}

	@Override
	public void removeDownloadListener(DownloadListener listener) {
	}

	@Override
	public void addListener(Listener listener) {
	}

	@Override
	public void removeListener(Listener listener) {
	}

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
		return new String[0];
	}

	@Override
	public String getAvailableVersion() {
		return context.getVersion();
	}

	@Override
	public void deferUpdate() {
	}

	@Override
	public boolean isUpdatesEnabled() {
		return false;
	}

	@Override
	public void checkForUpdate() throws IOException {
	}

	@Override
	public void update() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void shutdown() {
	}

	@Override
	public void checkIfBusAvailable() {
	}

}
