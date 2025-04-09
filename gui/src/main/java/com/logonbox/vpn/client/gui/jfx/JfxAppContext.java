/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
