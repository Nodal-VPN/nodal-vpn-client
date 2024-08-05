package com.logonbox.vpn.client.gui.jfx;

import com.logonbox.vpn.client.common.AppContext;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.nio.file.Path;
import java.nio.file.Paths;

public interface JfxAppContext<CONX extends IVpnConnection> extends AppContext<CONX> {

	String getUri();

	boolean isConnect();

	boolean isExitOnConnection();

	boolean isNoMinimize();

	boolean isNoClose();

	boolean isNoAddWhenNoConnections();

	boolean isNoResize();

    boolean isOptions();

	boolean isNoMove();

	boolean isNoSystemTray();

	boolean isCreateIfDoesntExist();

	default Path getTempDir() {
	    if(AppVersion.isDeveloperWorkspace()) {
	        return Paths.get("tmp");
	    }
	    else {
			return Paths.get(System.getProperty("java.io.tmpdir"));
	    }
	}

    boolean isConnectUri();
}
