package com.logonbox.vpn.client.desktop;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import com.logonbox.vpn.client.gui.jfx.Debugger;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.vladsch.javafx.webview.debugger.DevToolsDebuggerJsBridge;
import com.vladsch.javafx.webview.debugger.JfxScriptStateProvider;

import javafx.scene.web.WebView;

public class DefaultDebugger implements Debugger {
	private final static Logger LOG = LoggerFactory.getLogger(DefaultDebugger.class);

	private DevToolsDebuggerJsBridge myJSBridge;
	private final JfxScriptStateProvider myStateProvider;
	private final UIContext context;
	private WebView webView;
	
	public DefaultDebugger(UIContext context, JfxScriptStateProvider myStateProvider) {
		this.myStateProvider = myStateProvider;
		this.context = context;
	}
	
	public void start(WebView webView) {
		this.webView = webView;
		// create JSBridge Instance
		myJSBridge = new DevToolsJsBridge(webView, 0, myStateProvider);
	}

	@Override
	public void loadReady() {

		myJSBridge.connectJsBridge();

		/*
		 * *****************************************************************************
		 * ************ Optional: JavaScript event.preventDefault(),
		 * event.stopPropagation() and event.stopImmediatePropagation() don't work on
		 * Java registered listeners. The alternative mechanism is for JavaScript event
		 * handler to set
		 * markdownNavigator.setEventHandledBy("IdentifyingTextForDebugging") Then in
		 * Java event listener to check for this value not being null, and clear it for
		 * next use.
		 *******************************************************************************************/
		EventListener clickListener = evt -> {
			if (myJSBridge.getJSEventHandledBy() != null) {
				LOG.warn("onClick: default prevented by: " + myJSBridge.getJSEventHandledBy());
				myJSBridge.clearJSEventHandledBy();
			} else {
				Node element = (Node) evt.getTarget();
				Node id = element.getAttributes().getNamedItem("id");
				String idSelector = id != null ? "#" + id.getNodeValue() : "";
				LOG.debug("onClick: clicked on " + element.getNodeName() + idSelector);
			}
		};

		Document document = webView.getEngine().getDocument();
		((EventTarget) document).addEventListener("click", clickListener, false);

		instrumentHtml(document.getDocumentElement());
		
	}

	@Override
	public void launch() {

		String debuggerURL = myJSBridge.getDebuggerURL();
		for (String tool : new String[] { "chrome", "chromium-browser", "chromium" }) {
			ProcessBuilder pb = new ProcessBuilder(tool + (SystemUtils.IS_OS_WINDOWS ? ".exe" : ""), debuggerURL);
			pb.redirectErrorStream(true);
			try {
				Process p = pb.start();
				new Thread() {
					public void run() {
						try {
							p.getInputStream().transferTo(System.out);
						} catch (IOException e) {
						}
					}
				}.start();
				return;
			} catch (Exception e) {
			}
		}
		context.openURL(debuggerURL);
		
	}

	@Override
	public String getDebuggerURL() {
		return myJSBridge.getDebuggerURL();
	}

	public class DevToolsJsBridge extends DevToolsDebuggerJsBridge {
		public DevToolsJsBridge(final @NotNull WebView webView, final int instance,
				@Nullable final JfxScriptStateProvider stateProvider) {
			super(webView, webView.getEngine(), instance, stateProvider);
		}

		// need these to update menu item state
		@Override
		public void onConnectionOpen() {
			LOG.info("Chrome Dev Tools connected");
		}

		@Override
		public void onConnectionClosed() {
			LOG.info("Chrome Dev Tools disconnected");
		}
	}

	@Override
	public void pageReloading() {
		myJSBridge.pageReloading();
	}

	private void instrumentHtml(Element documentElement) {
		// now we add our script if not debugging, because it will be injected
		if (!myJSBridge.isDebugging()) {
			NodeList headEls = documentElement.getElementsByTagName("head");
			Element headEl = null;
			if (headEls.getLength() == 0) {
				headEl = documentElement.getOwnerDocument().createElement("head");
				;
				NodeList htmlEls = documentElement.getElementsByTagName("html");
				if (htmlEls.getLength() > 0) {
					htmlEls.item(0).appendChild(headEl);
				}
			} else
				headEl = (Element) headEls.item(0);
			if (headEl != null) {
				Element scriptEl = documentElement.getOwnerDocument().createElement("script");
				scriptEl.setAttribute("src", "markdown-navigator.js");
				headEl.appendChild(scriptEl);
			}
		}
		// inject the state if it exists
		if (myStateProvider != null && !myStateProvider.getState().isEmpty()) {
			NodeList bodyEls = documentElement.getElementsByTagName("body");
			if (bodyEls.getLength() > 0) {
				Element bodyEl = (Element) bodyEls.item(0);
				Element scriptEl = documentElement.getOwnerDocument().createElement("script");
				scriptEl.setTextContent(myJSBridge.getStateString());
				bodyEl.appendChild(scriptEl);
			}
		}
	}

	@Override
	public void startDebugging(Consumer<Throwable> onStartFail, Runnable onStartSuccess) {
		if (!myJSBridge.isDebuggerEnabled()) {
			int port = getPort();
			myJSBridge.startDebugServer(port, onStartFail, onStartSuccess);
		}
	}

	@Override
	public void stopDebugging(Consumer<Boolean> onStop) {
		if (myJSBridge.isDebuggerEnabled()) {
			myJSBridge.stopDebugServer(onStop);
		}
	}

	private int getPort() {
		assert myStateProvider != null;
		return myStateProvider.getState().getJsNumber("debugPort").intValue(51723);
	}
}
