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

import com.logonbox.vpn.client.common.BrandingManager.ImageHandler;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.util.Optional;

import javafx.scene.control.Alert.AlertType;

public interface UIContext<CONX extends IVpnConnection> {

	Navigator navigator();
	
	Styling styling();
	
	Optional<Debugger> debugger();
	
	JfxAppContext<CONX> getAppContext();

	boolean isTrayConfigurable();

	void exitApp();

	void openURL(String url);

	boolean isAllowBranding();

	void minimize();

	void reapplyBranding();

	void open();

	void maybeExit();

	boolean isMinimizeAllowed();

	boolean isUndecoratedWindow();
	
	boolean promptForCertificate(AlertType alertType, String title, String content, String key,
			String hostname, String message, PromptingCertManager mgr);

	boolean isDarkMode();

	void back();

	boolean isContextMenuAllowed();
	
	ImageHandler getImageHandler();

    void startTray();
}
