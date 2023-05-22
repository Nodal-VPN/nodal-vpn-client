package com.logonbox.vpn.client.gui.jfx;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.api.Branding;

import javafx.scene.Parent;
import javafx.scene.control.Alert.AlertType;

public interface UIContext {
	
	Navigator navigator();
	
	Styling styling();
	
	Optional<Debugger> debugger();
	
	AppContext getAppContext();

	AbstractDBusClient getDBus();

	boolean isTrayConfigurable();

	ExecutorService getOpQueue();

	void exitApp();

	void openURL(String url);

	boolean isAllowBranding();

	void minimize();

	void applyColors(Branding branding, Parent root);

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
