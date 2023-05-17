package com.logonbox.vpn.client.gui.jfx;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPowerMonitor implements PowerMonitor {


	final static Logger log = LoggerFactory.getLogger(AbstractPowerMonitor.class);

	private List<Listener> listeners = new ArrayList<>();

	@Override
	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

}
