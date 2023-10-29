package com.logonbox.vpn.client.gui.jfx;

import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import javafx.scene.control.Alert.AlertType;

public interface UIContext<CONX extends IVpnConnection> {

	Navigator navigator();
	
	Styling styling();
	
	Optional<Debugger> debugger();
	
	JfxAppContext<CONX> getAppContext();

	boolean isTrayConfigurable();

	ExecutorService getOpQueue();

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
}
