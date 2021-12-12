package com.logonbox.vpn.client.gui.jfx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PowerMonitor {

	public interface Listener {
		void wake();
	}

	final static Logger log = LoggerFactory.getLogger(PowerMonitor.class);

	public final static PowerMonitor get() {
		return new DumbPowerMonitor();
	}

	private List<Listener> listeners = new ArrayList<>();

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	abstract void start() throws IOException;

}
