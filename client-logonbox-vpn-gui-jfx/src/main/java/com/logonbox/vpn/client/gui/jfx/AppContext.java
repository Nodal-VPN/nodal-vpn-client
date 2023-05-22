package com.logonbox.vpn.client.gui.jfx;

import java.io.File;

import org.slf4j.event.Level;

public interface AppContext {

	void restart();

	String getUri();

	boolean isConnect();

	boolean isExitOnConnection();

	boolean isNoMinimize();

	boolean isNoClose();

	Level getDefaultLogLevel();

	boolean isNoAddWhenNoConnections();

	void exit();

	boolean isNoResize();

	boolean isNoMove();

	boolean isNoSystemTray();

	boolean isCreateIfDoesntExist();

	default File getTempDir() {
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			return new File(System.getProperty("java.io.tmpdir"));
		else
			return new File(System.getProperty("hypersocket.bootstrap.distDir")).getParentFile();
	}

	void setLevel(Level valueOf);
}
