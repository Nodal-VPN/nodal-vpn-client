package com.logonbox.vpn.client.gui.jfx;

import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public interface ClientExtension {

	void cleanUp();

	void start(Client client);

	void configure(WebView webView);

	void pageSucceeded();

	void load(String url);

	void configureWebEngine(WebEngine engine);
}
