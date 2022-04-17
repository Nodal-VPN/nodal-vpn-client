package com.logonbox.vpn.client.gui.jfx.debugger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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

import com.logonbox.vpn.client.gui.jfx.Client;
import com.logonbox.vpn.client.gui.jfx.ClientExtension;
import com.vladsch.boxed.json.BoxedJsObject;
import com.vladsch.boxed.json.BoxedJson;
import com.vladsch.javafx.webview.debugger.DevToolsDebuggerJsBridge;
import com.vladsch.javafx.webview.debugger.JfxScriptStateProvider;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class DebuggerExtension implements ClientExtension, JfxScriptStateProvider {

	final static Logger LOG = LoggerFactory.getLogger(DebuggerExtension.class);

	static private BoxedJsObject jsstate = BoxedJson.of(); // start with empty state

	private Client client;
	private DevToolsJsBridge myJSBridge;
	private WebView webView;
	
	@FXML
	private Parent debugBar;
	@FXML
	private Hyperlink debuggerLink;
	@FXML
	private Button startDebugger;
	@FXML
	private Button stopDebugger;

	@NotNull
	@Override
	public BoxedJsObject getState() {
		return jsstate;
	}

	@Override
	public void setState(@NotNull final BoxedJsObject state) {
		jsstate = state;
	}

	@Override
	public void cleanUp() {
		File stateFile = new File(client.getTempDir(), "debug.json");
		if (jsstate.isEmpty()) {
			stateFile.delete();
		} else {
			// save state for next run
			try {
				FileWriter stateWriter = new FileWriter(stateFile);
				stateWriter.write(jsstate.toString());
				stateWriter.flush();
				stateWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	public void start(Client client) {
		this.client = client;
		try {
			File stateFile = new File(client.getTempDir(), "debug.json");
			if (stateFile.exists()) {
				FileReader stateReader = new FileReader(stateFile);
				jsstate = BoxedJson.boxedFrom(stateReader);
				stateReader.close();
			}
		} catch (IOException e) {
		}

		if (!jsstate.isValid()) {
			jsstate = BoxedJson.of();
		}

	}

	@Override
	public void configure(WebView webView) {
		this.webView = webView;
		webView.setContextMenuEnabled(true);
		myJSBridge = new DevToolsJsBridge(webView, 0, this);
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
	public void pageSucceeded() {

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
	public void load(String url) {
		if (myJSBridge != null)
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
		if (getState().isEmpty()) {
			NodeList bodyEls = documentElement.getElementsByTagName("body");
			if (bodyEls.getLength() > 0) {
				Element bodyEl = (Element) bodyEls.item(0);
				Element scriptEl = documentElement.getOwnerDocument().createElement("script");
				scriptEl.setTextContent(myJSBridge.getStateString());
				bodyEl.appendChild(scriptEl);
			}
		}
	}

	private void startDebugging(Consumer<Throwable> onStartFail, Runnable onStartSuccess) {
		if (!myJSBridge.isDebuggerEnabled()) {
			int port = getPort();
			myJSBridge.startDebugServer(port, onStartFail, onStartSuccess);
		}
	}

	private void stopDebugging(Consumer<Boolean> onStop) {
		if (myJSBridge.isDebuggerEnabled()) {
			myJSBridge.stopDebugServer(onStop);
		}
	}

	private int getPort() {
		return getState().getJsNumber("debugPort").intValue(51723);
	}

	@Override
	public void configureWebEngine(WebEngine engine) {
		debugBar.managedProperty().bind(debugBar.visibleProperty());
		stopDebugger.disableProperty().bind(Bindings.not(startDebugger.disableProperty()));
	}

	@FXML
	private void evtLaunchDebugger() {
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
		client.getHostServices().showDocument(debuggerURL);
	}

	@FXML
	private void evtStartDebugger() {
		startDebugging((ex) -> {
			// failed to start
			LOG.error("Debug server failed to start: " + ex.getMessage());
			debuggerLink.textProperty().set("");
			startDebugger.setDisable(false);
//             updateDebugOff.run();
		}, () -> {
			LOG.info("Debug server started, debug URL: " + myJSBridge.getDebuggerURL());
			debuggerLink.textProperty().set("Launch Chrome Debugger");
			startDebugger.setDisable(true);
			// can copy debug URL to clipboard
//             updateDebugOn.run();
		});
		startDebugging(null, null);
	}

	@FXML
	private void evtStopDebugger() {
		stopDebugging((e) -> {
			startDebugger.setDisable(false);
			debuggerLink.textProperty().set("");
		});
	}
}
