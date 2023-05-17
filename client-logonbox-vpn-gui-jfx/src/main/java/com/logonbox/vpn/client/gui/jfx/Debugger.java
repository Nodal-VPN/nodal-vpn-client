package com.logonbox.vpn.client.gui.jfx;

import java.util.function.Consumer;

import javafx.scene.web.WebView;

public interface Debugger {

	void start(WebView webView);

	void loadReady();

	void launch();

	String getDebuggerURL();

	void pageReloading();

	void startDebugging(Consumer<Throwable> onStartFail, Runnable onStartSuccess);

	void stopDebugging(Consumer<Boolean> onStop);

}
