package com.logonbox.vpn.client.gui.swt;

import static com.logonbox.vpn.client.common.Utils.isBlank;
import static com.logonbox.vpn.drivers.lib.util.Util.toHumanSize;

import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.dbus.VPN;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.common.lbapi.Branding;
import com.sshtools.jaul.UpdateService;

import org.eclipse.swt.browser.Browser;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

public class PageModel2 {
	
	static boolean EXPAND_HTML_RESOURCES = Boolean.getBoolean("swt.html.expandResources");
	
	final static Logger LOG = LoggerFactory.getLogger(PageModel2.class);
	
	public interface Page {
		Map<String, Object> beans();
	}
	private ResourceBundle pageBundle;
	private Document document;
	private boolean processed;
	private String userStyleSheetLocation;
	private String lastErrorCause;
	private String lastErrorMessage;
	private String lastException;
	private Branding branding;
	private String disconnectionReason;
	private Browser browser;
	private VpnConnection connection;
	private ServerBridge2 bridge;
	private Map<String, Page> pages = new HashMap<>();
	private BoundObjects2 jsobj;
	private String htmlPage;
	private URL url;

    private final Client client;
    private final VpnManager<VpnConnection> vpnManager;
    private final UpdateService updateService;
    private final ResourceBundle resources;

	public PageModel2(Client client, UpdateService updateService, ResourceBundle resources) {
		this.client = client;
		this.vpnManager = client.getApp().getVpnManager();
		this.updateService = updateService;
		this.resources = resources;
	}
	
	public void setUrl(URL url) {
		this.url = url;
	}

	public URL getUrl() {
		return url;
	}

	public Document getDocument() {
		return document;
	}

	public VpnConnection getConnection() {
		return connection;
	}

	public String getHtmlPage() {
		return htmlPage;
	}

	public void setHtmlPage(String htmlPage) {
		this.htmlPage = htmlPage;
	}

	public void addPage(String page, Page impl) {
		pages.put(page, impl);
	}

	public ServerBridge2 getBridge() {
		return bridge;
	}

	public void setBridge(ServerBridge2 bridge) {
		this.bridge = bridge;
	}

	public Browser getBrowser() {
		return browser;
	}

	public void setBrowser(Browser browser) {
		this.browser = browser;
	}

	public String getDisconnectionReason() {
		return disconnectionReason;
	}

	public void setDisconnectionReason(String disconnectionReason) {
		this.disconnectionReason = disconnectionReason;
	}

	public ResourceBundle getPageBundle() {
		return pageBundle;
	}

	public void setPageBundle(ResourceBundle pageBundle) {
		this.pageBundle = pageBundle;
	}

	public Branding getBranding() {
		return branding;
	}

	public void setBranding(Branding branding) {
		this.branding = branding;
	}

	public void reset() {
		if (LOG.isInfoEnabled())
			LOG.info("Resetting processor");
		processed = false;
		lastErrorCause = null;
		lastErrorMessage = null;
		lastException = null;
	}

	public boolean isProcessed() {
		return processed;
	}

	public String getUserStyleSheetLocation() {
		return userStyleSheetLocation;
	}

	public void setUserStyleSheetLocation(String userStyleSheetLocation) {
		this.userStyleSheetLocation = userStyleSheetLocation;
	}

	public void setDocument(Document document) {
		this.document = document;
	}

	public void setConnection(VpnConnection connection) {
		this.connection = connection;
	}

	public void process(boolean workOnDocument) {
		disposeJavascript();
		
		if (LOG.isInfoEnabled())
			LOG.info("Processing binding setup");
		
		var vpn = vpnManager.getVpn().orElse(null);
		var actualReason = connection == null ? disconnectionReason : connection.getLastError();
		var collections = new HashMap<String, Collection<String>>();

		String errorText = "";
		String exceptionText = "";
		String errorCauseText = lastErrorCause == null ? "" : lastErrorCause;

		if (lastException != null) {
			exceptionText = lastException;
		}
		if (lastErrorMessage != null) {
			errorText = lastErrorMessage;
		}

		/* VPN service */


		var replacements = new HashMap<String, String>();
		replacements.put("automaticUpdates",
				vpn == null ? "true" : vpn.getValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey()));
		replacements.put("updatesEnabled", String.valueOf(updateService.isUpdatesEnabled()));
		replacements.put("needsUpdating", String.valueOf(updateService.isNeedsUpdating()));
		long vpnFreeMemory = vpn == null ? 0 : vpn.getFreeMemory();
		long vpnMaxMemory = vpn == null ? 0 : vpn.getMaxMemory();
		replacements.put("serviceFreeMemory", toHumanSize(vpnFreeMemory));
		replacements.put("serviceMaxMemory", toHumanSize(vpnMaxMemory));
		replacements.put("serviceUsedMemory", toHumanSize(vpnMaxMemory - vpnFreeMemory));
		replacements.put("availableVersion", vpn == null ? ""
				: MessageFormat.format(resources.getString("availableVersion"), updateService.getAvailableVersion()));
		replacements.put("installingVersion", vpn == null ? ""
				: MessageFormat.format(resources.getString("installingVersion"), updateService.getAvailableVersion()));

		/* General */
		long freeMemory = Runtime.getRuntime().freeMemory();
		replacements.put("freeMemory", toHumanSize(freeMemory));
		long maxMemory = Runtime.getRuntime().maxMemory();
		replacements.put("maxMemory", toHumanSize(maxMemory));
		replacements.put("usedMemory", toHumanSize(maxMemory - freeMemory));
		replacements.put("errorMessage", errorText);
		replacements.put("errorCauseMessage", errorCauseText);
		replacements.put("exception", exceptionText);
		var version = client.getApp().getVersion();
		replacements.put("clientVersion", version);
		replacements.put("snapshot", String.valueOf(version.indexOf("-SNAPSHOT") != -1));
		replacements.put("brand",
				MessageFormat.format(resources.getString("brand"),
						(branding == null || branding.resource() == null
								|| isBlank(branding.resource().name()) ? "LogonBox"
										: branding.resource().name())));
		replacements.put("trayConfigurable", String.valueOf(client.isTrayConfigurable()));

		/* Connection */
		replacements.put("displayName", connection == null ? "" : connection.getDisplayName());
		replacements.put("name", connection == null || connection.getName() == null ? "" : connection.getName());
		replacements.put("interfaceName",
				connection == null || connection.getInterfaceName() == null ? "" : connection.getInterfaceName());
		replacements.put("server", connection == null ? "" : connection.getHostname());
		replacements.put("serverUrl", connection == null ? "" : connection.getUri(false));
		replacements.put("port", connection == null ? "" : String.valueOf(connection.getPort()));
		replacements.put("endpoint",
				connection == null ? "" : connection.getEndpointAddress() + ":" + connection.getEndpointPort());
		replacements.put("publicKey", connection == null ? "" : connection.getPublicKey());
		replacements.put("userPublicKey", connection == null ? "" : connection.getUserPublicKey());
		replacements.put("address", connection == null ? "" : connection.getAddress());
		replacements.put("usernameHint", connection == null ? "" : connection.getUsernameHint());
		replacements.put("connectAtStartup",
				connection == null ? "false" : String.valueOf(connection.isConnectAtStartup()));
		replacements.put("stayConnected", connection == null ? "false" : String.valueOf(connection.isStayConnected()));
		replacements.put("allowedIps", connection == null ? "" : String.join(", ", connection.getAllowedIps()));
		replacements.put("dns", connection == null ? "" : String.join(", ", connection.getDns()));
		replacements.put("persistentKeepalive",
				connection == null ? "" : String.valueOf(connection.getPersistentKeepalive()));
		replacements.put("disconnectionReason", actualReason == null ? "" : actualReason);
		String statusType = connection == null ? "" : connection.getStatus();
		replacements.put("status", statusType);
		replacements.put("connected", String.valueOf(statusType.equals(ConnectionStatus.Type.CONNECTED.name())));
		replacements.put("connecting", String.valueOf(statusType.equals(ConnectionStatus.Type.CONNECTING.name())));
		replacements.put("disconnected", String.valueOf(statusType.equals(ConnectionStatus.Type.DISCONNECTED.name())));
		replacements.put("disconnecting",
				String.valueOf(statusType.equals(ConnectionStatus.Type.DISCONNECTING.name())));
		if (connection == null || !statusType.equals(ConnectionStatus.Type.CONNECTED.name())) {
			replacements.put("lastHandshake", "");
			replacements.put("usage", "");
		} else {
			replacements.put("lastHandshake",
					DateFormat.getDateTimeInstance().format(new Date(connection.getLastHandshake())));
			replacements.put("usage", MessageFormat.format(resources.getString("usageDetail"),
					toHumanSize(connection.getRx()), toHumanSize(connection.getTx())));
		}

		if (LOG.isInfoEnabled())
			LOG.info("Processing bindings");
		var baseHtmlPage = getBaseHtml();

		jsobj = new BoundObjects2(browser);
		jsobj.bind(ServerBridge.class, "bridge", bridge);
		if(!isRemote()) {
			try {
				jsobj.bind(VPN.class, "vpn", vpn);
			} catch (IllegalStateException ise) {
				/* No bus */
			}
			if (connection != null)
				jsobj.bind(IVpnConnection.class, "connection", connection);
			jsobj.bind(ResourceBundle.class, "pageBundle", pageBundle);
			
			var pg = pages.get(baseHtmlPage);
			if(pg != null) {
				for(var en : pg.beans().entrySet()) {
					var binding = jsobj.bind(en.getValue().getClass(), en.getKey(), en.getValue());
					if(LOG.isDebugEnabled())
						LOG.debug(String.format(" Setting %s to %s", en.getKey(), binding.toJs()));
				}
			}
	
			for (var c : bridge.getConnections())
				jsobj.bind(IVpnConnection.class, c);
			
			if(workOnDocument) {
				var script = document.createElement("script");
				script.attr("type", "text/javascript");
				script.html(jsobj.toJs());
				document.body().appendChild(script);
				
				if(EXPAND_HTML_RESOURCES) {
					var removeNodes = new HashSet<Element>();
					var styleNodes = new ArrayList<Element>();
					for(var l : document.getElementsByTag("link")) {
						if("stylesheet".equals(l.attr("rel"))) {
							var href = l.attr("href");
							if(!href.startsWith("http:") && !href.startsWith("https:")) {
								LOG.info("Expanding CSS {}", href);
								var url = Http.url(getBase(), href);
								try (var sin = url.openStream()) {
									styleNodes.add(document.createElement("style").attr("type", "text/css")
											.html(Utils.toString(sin, "UTF-8")));
								} catch (IOException ioe) {
									throw new IllegalStateException("Failed to embed.", ioe);
								}
								removeNodes.add(l);
							}
						}
					}
					for(var l : document.getElementsByTag("script")) {
						if(l.hasAttr("src")) {
							var src = l.attr("src");
							LOG.info("Expanding Javascript {}", src);
							var url = Http.url(getBase(), src);
							try (var sin = url.openStream()) {
								l.html(Utils.toString(sin, "UTF-8"));
								l.removeAttr("src");
								l.attr("type", "text/javascript");
							} catch (IOException ioe) {
								throw new IllegalStateException("Failed to embed.", ioe);
							}
						}
					}
					for (Element n : removeNodes)
						n.remove();
					document.body().insertChildren(0, styleNodes);
				}
			}
			else {
				if(LOG.isDebugEnabled())
					LOG.debug("Evaluate : {}", jsobj.toJs());
				browser.evaluate(jsobj.toJs(), true);
			}
			
			if (LOG.isInfoEnabled())
				LOG.info("Processing page content");
	
			if(workOnDocument) {
				var head = document.selectFirst("head");
				if(!EXPAND_HTML_RESOURCES) {
					var base = document.createElement("base");
					base.attr("href", getBase().toExternalForm());
					head.insertChildren(0, base);//
				}
				else {
					document.select("base").remove();
				}
		
				if (userStyleSheetLocation != null) {
					// <link href="fontawesome/css/font-awesome.min.css" rel="stylesheet">
					var ucss = document.createElement("link");
					ucss.attr("href", userStyleSheetLocation);
					ucss.attr("type", "text/css");
					ucss.attr("media", "all");
					ucss.attr("rel", "stylesheet");
					ucss.attr("id", "localCss");
					head.appendChild(ucss);
				}

				var newNodes = new HashMap<Element, Collection<Element>>();
				var removeNodes = new HashSet<Element>();
				dataAttributes(document, newNodes, removeNodes, collections, replacements);
				for (Map.Entry<Element, Collection<Element>> en : newNodes.entrySet()) {
					for (Element n : en.getValue())
						en.getKey().appendChild(n);
				}
				for (Element n : removeNodes)
					n.remove();
				
				document.body().appendChild(document.createElement("script").attr("type", "text/javascript").html("""
						var elements = document.getElementsByTagName('a');
						for(var i = 0, len = elements.length; i < len; i++) {
							var el = elements[i];
						    el.onclick = function (e) {
								var hr = this.getAttribute('href');
								if(hr.startsWith('#') || hr.startsWith('http:') || hr.startsWith('https:'))
									return;
								e.preventDefault();
						        bridge.page(hr);
						    }
						}			
									"""));
			}
			else {
				var scr = new StringBuilder("window.replacements = { ");
				if(!isRemote()) {
					boolean first = true;
					for(var en : replacements.entrySet()) {
						if(first) {
							first = false;
						}
						else {
							scr.append(",");
						}
						scr.append(en.getKey());
						scr.append(":");
						scr.append("'");
						scr.append(Utils.escapeEcmaScript(en.getValue()));
						scr.append("'");
					}
					scr.append("};\n");
	
					scr.append("""
	if (document.getElementsByTagName('base').length == 0)
	{
	    var head  = document.getElementsByTagName('head')[0];
	    var link  = document.createElement('base');
	    link.href = '${href}';
	    head.appendChild(link);
	}
	""".replace("${href}",  getBase().toExternalForm()));
				}
				
				if(userStyleSheetLocation != null) {
					scr.append("""
var cssId = 'localCss';
if (!document.getElementById(cssId))
{
    var head  = document.getElementsByTagName('head')[0];
    var link  = document.createElement('link');
    link.id   = cssId;
    link.rel  = 'stylesheet';
    link.type = 'text/css';
    link.href = '${href}';
    link.media = 'all';
    head.appendChild(link);
}
""".replace("${href}", userStyleSheetLocation));
				}
				
	
				scr.append("""
function visit(node) {
    var atts = node.attributes;
    if(atts) {	
	    for (var att, i = 0, n = atts.length; i < n; i++) {
	        att = atts[i];
	        var val = att.nodeValue;
	        var name = att.nodeName;
	        
	        if(name === 'data-conditional') {
	            // TODO
	        }
	        else if (name.startsWith('data-attr-i18n-')) {
	            var attrVal = pageBundle == null ? '?' : pageBundle.getString(val);
				var attrName = name.substring(15);
				node.setAttribute(attrName, attrVal);
	        } else {
		        if (name === 'data-attr-value') {
			        node.setAttribute(node.getAttribute('data-attr-name'), replacements[val]);
		        } else if (name === 'data-i18n') {
	                //List<Object> args = new ArrayList<>();
	                //for (int ai = 0; ai < 9; ai++) {
	                	//String i18nArg = node.attr("data-i18n-" + ai);
	                	//if (i18nArg != null) {
	                		//args.add(replacements.get(i18nArg));
	                	//}
	                //}
	                var attrVal;
	                try {
		                attrVal = pageBundle.getString(val);
	                } catch (e) {
		                attrVal = resources.getString(val);
	                }
	                //if (!args.isEmpty()) {
	                	//attrVal = MessageFormat.format(attrVal, args.toArray(new Object[0]));
	                //}
	                node.innerHTML = attrVal;
				} else if (name === 'data-collection') {
					// TODO
				} else if (name === 'data-content') {
					node.innerHTML = replacements[val];
				}
	        }
	    }
	}
    for (var i=0; i<node.children.length; i++)
        visit(node.children[i]);
}
visit(document);
""");
				scr.append("""
						var elements = document.getElementsByTagName('a');
						for(var i = 0, len = elements.length; i < len; i++) {
							var el = elements[i];
						    el.onclick = function (e) {
								var hr = this.getAttribute('href');
								if(hr.startsWith('#') || hr.startsWith('http:') || hr.startsWith('https:'))
									return;
								e.preventDefault();
						        bridge.page(hr);
						    }
						}			
									""");
				
				browser.evaluate(scr.toString(), true);
				
			}
		}
		
		
		processed = true;
		
		if (LOG.isInfoEnabled())
			LOG.info("Processing done");
	}

	public boolean isRemote() {
		return htmlPage == null ? false: Http.isRemote(htmlPage);
	}
	
	public URL getBase() {
		return Http.parentURL(url);
	}
	
	public String getAnchor() {
		if (htmlPage == null)
			return null;
		int idx = htmlPage.indexOf('#');
		if (idx != -1) {
			return htmlPage.substring(idx + 1);
		}
		return null;
	}

	public String getBaseHtml() {
		String htmlPage = this.htmlPage;
		if (htmlPage == null)
			return htmlPage;
		int idx = htmlPage.indexOf('?');
		if (idx != -1) {
			htmlPage = htmlPage.substring(0, idx);
		}
		idx = htmlPage.indexOf('#');
		if (idx != -1) {
			htmlPage = htmlPage.substring(0, idx);
		}
		return htmlPage;
	}

	private void disposeJavascript() {
		if (jsobj != null) {
			LOG.info("Disposing previous jsobj");
			jsobj.dispose();
			LOG.info("Disposed previous jsobj");
			jsobj = null;
		}
	}

	protected void dataAttributes(Element node, Map<Element, Collection<Element>> newNodes, Set<Element> removeNodes,
			Map<String, Collection<String>> collections, Map<String, String> replacements) {

		var attrs = node.attributes();
		for (var attr : attrs) {
			String val = attr.getValue();
			if (attr.getKey().equals("data-conditional")) {
				boolean not = false;
				if (val != null && val.startsWith("!")) {
					not = true;
					val = val.substring(1);
				}
				String valVal = replacements.get(val);
				if (not) {
					if (valVal == null || valVal.length() == 0 || valVal.equals("false") || valVal.equals("0")) {
						// Leave
					} else {
						node.remove();
						return;
					}
				} else {
					if (valVal != null && valVal.length() > 0 && !valVal.equals("false") && !valVal.equals("0")) {
						// Leave
					} else {
						node.remove();
						return;
					}
				}
			} else if (attr.getKey().startsWith("data-attr-i18n-")) {
				String attrVal = pageBundle == null ? "?" : pageBundle.getString(val);
				String attrName = attr.getKey().substring(15);
				node.attr(attrName, attrVal);
			} else {
				if (attr.getKey().equals("data-attr-value")) {
					node.attr(node.attr("data-attr-name"), replacements.get(val));
				} else if (attr.getKey().equals("data-i18n")) {
					List<Object> args = new ArrayList<>();
					for (int ai = 0; ai < 9; ai++) {
						String i18nArg = node.attr("data-i18n-" + ai);
						if (i18nArg != null) {
							args.add(replacements.get(i18nArg));
						}
					}
					String attrVal;
					try {
						attrVal = pageBundle.getString(val);
					} catch (MissingResourceException mre) {
						attrVal = resources.getString(val);
					}
					if (!args.isEmpty()) {
						attrVal = MessageFormat.format(attrVal, args.toArray(new Object[0]));
					}
					node.text(attrVal);
				} else if (attr.getKey().equals("data-collection")) {
					Collection<String> collection = collections.get(val);
					if (collection == null)
						LOG.warn(String.format("No collection named %s", val));
					else {
						List<Element> newCollectionNodes = new ArrayList<>();
						for (String elVal : collection) {
							Element template = node.shallowClone();
							template.text(elVal);
							newCollectionNodes.add(template);
						}
						newNodes.put(node.parent(), newCollectionNodes);
						removeNodes.add(node);
					}
				} else if (attr.getKey().equals("data-content")) {
					node.text(replacements.get(val));
				}
			}
		}

		for (var n : node.children()) {
			dataAttributes(n, newNodes, removeNodes, collections, replacements);
		}
	}

	public void error(String error, String cause, String exception) {
		LOG.error(error, exception);
		lastErrorCause = cause;
		lastErrorMessage = error;
		lastException = exception;
	}
}
