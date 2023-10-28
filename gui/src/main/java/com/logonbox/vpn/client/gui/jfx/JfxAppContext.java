package com.logonbox.vpn.client.gui.jfx;

import com.logonbox.vpn.client.common.AppContext;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.io.File;

public interface JfxAppContext<CONX extends IVpnConnection> extends AppContext<CONX> {

	String getUri();

	boolean isConnect();

	boolean isExitOnConnection();

	boolean isNoMinimize();

	boolean isNoClose();

	boolean isNoAddWhenNoConnections();

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
}
