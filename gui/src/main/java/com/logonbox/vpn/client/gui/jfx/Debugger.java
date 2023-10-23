package com.logonbox.vpn.client.gui.jfx;

import java.io.Closeable;
import java.util.function.Consumer;

import javafx.scene.web.WebView;

public interface Debugger extends Closeable {

	void start(WebView webView);

	void loadReady();

	void launch();

	String getDebuggerURL();

	void pageReloading();

	void startDebugging(Consumer<Throwable> onStartFail, Runnable onStartSuccess);

	void stopDebugging(Consumer<Boolean> onStop);
	
	@Override
	void close();

    void setup(UIContext<?> context);

}
