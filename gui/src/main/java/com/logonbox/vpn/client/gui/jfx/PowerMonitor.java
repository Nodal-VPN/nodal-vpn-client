package com.logonbox.vpn.client.gui.jfx;

import java.util.Optional;
import java.util.ServiceLoader;

public interface PowerMonitor {

	public static Optional<PowerMonitor> get() {
		return ServiceLoader.load(PowerMonitor.class).findFirst();
	}

	public interface Listener {
		void wake();
	}

	void addListener(Listener listener);

	void removeListener(Listener listener);

	void start();
}
