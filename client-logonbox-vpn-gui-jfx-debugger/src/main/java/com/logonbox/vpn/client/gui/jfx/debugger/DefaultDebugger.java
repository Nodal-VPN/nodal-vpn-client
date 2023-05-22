package com.logonbox.vpn.client.gui.jfx.debugger;

import com.logonbox.vpn.client.gui.jfx.Debugger;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.vladsch.boxed.json.BoxedJsObject;
import com.vladsch.boxed.json.BoxedJson;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Consumer;

import javafx.scene.web.WebView;

public class DefaultDebugger implements Debugger, JfxScriptStateProvider {

    static private BoxedJsObject jsstate = BoxedJson.of(); // start with empty state
    
	private final static Logger LOG = LoggerFactory.getLogger(DefaultDebugger.class);

	private DevToolsDebuggerJsBridge jsBridge;
	private UIContext context;
	private WebView webView;
	
    @Override
    public void setup(UIContext context) {
        this.context = context;
        try {
            var stateFile = new File(context.getAppContext().getTempDir(),"debug.json");
            if (stateFile.exists()) {
                try(var stateReader = new FileReader(stateFile)) {
                    jsstate = BoxedJson.boxedFrom(stateReader);
                }
            }
        } catch (IOException e) {
        }

        if (!jsstate.isValid()) {
            jsstate = BoxedJson.of();
        }
        
    }
	
	public void start(WebView webView) {
		this.webView = webView;
		// create JSBridge Instance
		jsBridge = new DevToolsJsBridge(webView, 0, this);
	}

	@Override
	public void loadReady() {

		jsBridge.connectJsBridge();

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
			if (jsBridge.getJSEventHandledBy() != null) {
				LOG.warn("onClick: default prevented by: " + jsBridge.getJSEventHandledBy());
				jsBridge.clearJSEventHandledBy();
			} else {
				Node element = (Node) evt.getTarget();
				Node id = element.getAttributes().getNamedItem("id");
				String idSelector = id != null ? "#" + id.getNodeValue() : "";
				LOG.debug("onClick: clicked on " + element.getNodeName() + idSelector);
			}
		};

		var document = webView.getEngine().getDocument();
		((EventTarget) document).addEventListener("click", clickListener, false);

		instrumentHtml(document.getDocumentElement());
		
	}

	@Override
	public void launch() {

		var debuggerURL = jsBridge.getDebuggerURL();
		for (String tool : new String[] { "chrome", "chromium-browser", "chromium" }) {
			var pb = new ProcessBuilder(tool + (SystemUtils.IS_OS_WINDOWS ? ".exe" : ""), debuggerURL);
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
		return jsBridge.getDebuggerURL();
	}

	public class DevToolsJsBridge extends DevToolsDebuggerJsBridge {
		public DevToolsJsBridge(final WebView webView, final int instance,
				final JfxScriptStateProvider stateProvider) {
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
		jsBridge.pageReloading();
	}

	private void instrumentHtml(Element documentElement) {
		// now we add our script if not debugging, because it will be injected
		if (!jsBridge.isDebugging()) {
			var headEls = documentElement.getElementsByTagName("head");
			Element headEl = null;
			if (headEls.getLength() == 0) {
				headEl = documentElement.getOwnerDocument().createElement("head");
				;
				var htmlEls = documentElement.getElementsByTagName("html");
				if (htmlEls.getLength() > 0) {
					htmlEls.item(0).appendChild(headEl);
				}
			} else
				headEl = (Element) headEls.item(0);
			if (headEl != null) {
				var scriptEl = documentElement.getOwnerDocument().createElement("script");
				scriptEl.setAttribute("src", "markdown-navigator.js");
				headEl.appendChild(scriptEl);
			}
		}
		// inject the state if it exists
		if (!getState().isEmpty()) {
			var bodyEls = documentElement.getElementsByTagName("body");
			if (bodyEls.getLength() > 0) {
				var bodyEl = (Element) bodyEls.item(0);
				var scriptEl = documentElement.getOwnerDocument().createElement("script");
				scriptEl.setTextContent(jsBridge.getStateString());
				bodyEl.appendChild(scriptEl);
			}
		}
	}

	@Override
	public void startDebugging(Consumer<Throwable> onStartFail, Runnable onStartSuccess) {
		if (!jsBridge.isDebuggerEnabled()) {
			int port = getPort();
			jsBridge.startDebugServer(port, onStartFail, onStartSuccess);
		}
	}

	@Override
	public void stopDebugging(Consumer<Boolean> onStop) {
		if (jsBridge.isDebuggerEnabled()) {
			jsBridge.stopDebugServer(onStop);
		}
	}

    @Override
    public void setState(final BoxedJsObject state) {
        jsstate = state;
    }

    @Override
    public BoxedJsObject getState() {
        return jsstate;
    }

	private int getPort() {
		return getState().getJsNumber("debugPort").intValue(51723);
	}

    @Override
    public void close() {
        var stateFile = new File(context.getAppContext().getTempDir(), "debug.json");
        if (jsstate.isEmpty()) {
            stateFile.delete();
        } else {
            // save state for next run
            try {
                var stateWriter = new FileWriter(stateFile);
                stateWriter.write(jsstate.toString());
                stateWriter.flush();
                stateWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }
}
