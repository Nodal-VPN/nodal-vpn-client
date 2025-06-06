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


import static com.jadaptive.nodal.core.lib.util.Util.toHumanSize;

import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.lbapi.Branding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

public class DOMProcessor<CONX extends IVpnConnection> {
	final static Logger log = LoggerFactory.getLogger(DOMProcessor.class);

	private Map<String, String> replacements = new HashMap<>();
	private Map<Node, Collection<Node>> newNodes = new HashMap<>();
	private Set<Node> removeNodes = new HashSet<>();
	private Element documentElement;
	private ResourceBundle pageBundle;
	private ResourceBundle resources;
	private Map<String, Collection<String>> collections;

	public DOMProcessor(UIContext<CONX> context, IVpn<CONX> vpn, IVpnConnection connection, Map<String, Collection<String>> collections,
			String lastErrorMessage, String lastErrorCause, String lastException, Branding branding,
			ResourceBundle pageBundle, ResourceBundle resources, Element documentElement, String disconnectionReason) {

		var updateService = context.getAppContext().getUpdateService();

		var errorText = "";
		var exceptionText = "";
		var errorCauseText = lastErrorCause == null ? "" : lastErrorCause;

		if (lastException != null) {
			exceptionText = lastException;
		}
		if (lastErrorMessage != null) {
			errorText = lastErrorMessage;
		}
        var vpnFreeMemory = vpn == null ? 0 : vpn.getFreeMemory();
        var vpnMaxMemory = vpn == null ? 0 : vpn.getMaxMemory();
        var freeMemory = Runtime.getRuntime().freeMemory();
        var maxMemory = Runtime.getRuntime().maxMemory();
        var version = context.getAppContext().getVersion();
        var statusType = connection == null ? "" : connection.getStatus();

		/* VPN service */

		replacements.put("automaticUpdates",
				vpn == null ? "true"
						: vpn.getValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey()));
		replacements.put("singleActiveConnection",
				vpn == null ? "true"
						: vpn.getValue(ConfigurationItem.SINGLE_ACTIVE_CONNECTION.getKey()));
		replacements.put("mtu", vpn == null ? "0"
						: vpn.getValue(ConfigurationItem.MTU.getKey()));
		replacements.put("updatesEnabled", String.valueOf(updateService.isUpdatesEnabled()));
		replacements.put("needsUpdating", String.valueOf(updateService.isNeedsUpdating()));
		replacements.put("serviceFreeMemory", toHumanSize(vpnFreeMemory));
		replacements.put("serviceMaxMemory", toHumanSize(vpnMaxMemory));
		replacements.put("serviceUsedMemory", toHumanSize(vpnMaxMemory - vpnFreeMemory));
		replacements.put("availableVersion", vpn == null ? ""
				: MessageFormat.format(resources.getString("availableVersion"), updateService.getAvailableVersion()));
		replacements.put("installingVersion", vpn == null ? ""
				: MessageFormat.format(resources.getString("installingVersion"), updateService.getAvailableVersion()));

		/* General */
		replacements.put("freeMemory", toHumanSize(freeMemory));
		replacements.put("maxMemory", toHumanSize(maxMemory));
		replacements.put("usedMemory", toHumanSize(maxMemory - freeMemory));
		replacements.put("errorMessage", errorText);
		replacements.put("errorCauseMessage", errorCauseText);
		replacements.put("exception", exceptionText);
		replacements.put("clientVersion", version);
		replacements.put("snapshot", String.valueOf(version.endsWith("-0") || version.indexOf("-SNAPSHOT") != -1));
		replacements.put("licensedTo",
				MessageFormat.format(resources.getString("licensedTo"),
						(branding == null || branding.resource() == null
								|| Utils.isBlank(branding.resource().name()) ? "JADAPTIVE"
										: branding.resource().name())));
		replacements.put("trayConfigurable", String.valueOf(context.isTrayConfigurable()));

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
		replacements.put("disconnectionReason", disconnectionReason == null ? "" : disconnectionReason);
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

		this.documentElement = documentElement;
		this.pageBundle = pageBundle;
		this.resources = resources;
		this.collections = collections;
	}

	public void process() {
	    if(documentElement != null)
	        dataAttributes(documentElement, newNodes, removeNodes);
		
		for (var en : newNodes.entrySet()) {
			for (Node n : en.getValue())
				en.getKey().appendChild(n);
		}
		
		for (var n : removeNodes)
			n.getParentNode().removeChild(n);
	}

	protected void dataAttributes(Element node, Map<Node, Collection<Node>> newNodes, Set<Node> removeNodes) {

		var attrs = node.getAttributes();
		for (var i = 0; i < attrs.getLength(); i++) {
			var attr = attrs.item(i);
			var val = attr.getNodeValue();
			if (attr.getNodeName().equals("data-conditional")) {
				var not = false;
				if (val != null && val.startsWith("!")) {
					not = true;
					val = val.substring(1);
				}
				String valVal = replacements.get(val);
				if (not) {
					if (valVal == null || valVal.length() == 0 || valVal.equals("false") || valVal.equals("0")) {
						// Leave
					} else {
						node.getParentNode().removeChild(node);
						return;
					}
				} else {
					if (valVal != null && valVal.length() > 0 && !valVal.equals("false") && !valVal.equals("0")) {
						// Leave
					} else {
						node.getParentNode().removeChild(node);
						return;
					}
				}
			} else if (attr.getNodeName().startsWith("data-attr-i18n-")) {
				var attrVal = pageBundle == null ? "?" : pageBundle.getString(val);
				var attrName = attr.getNodeName().substring(15);
				node.setAttribute(attrName, attrVal);
			} else {
				if (attr.getNodeName().equals("data-attr-value")) {
					node.setAttribute(node.getAttribute("data-attr-name"), replacements.get(val));
				} else if (attr.getNodeName().equals("data-i18n")) {
					var args = new ArrayList<Object>();
					for (var ai = 0; ai < 9; ai++) {
						var i18nArg = node.getAttribute("data-i18n-" + ai);
						if (i18nArg != null) {
							args.add(replacements.get(i18nArg));
						}
					}
					String attrVal;
					try {
						attrVal = pageBundle.getString(val);
					} catch (MissingResourceException mre) {
					    try {
					        attrVal = resources.getString(val);
					    }
					    catch(MissingResourceException mre2) {
					        attrVal = null;
					    }
					}
					if (attrVal != null && !args.isEmpty()) {
						attrVal = MessageFormat.format(attrVal, args.toArray(new Object[0]));
					}
					if(attrVal == null) {
					    attrVal = "[missing:" + val + "]";
					}
					node.setTextContent(attrVal);
				} else if (attr.getNodeName().equals("data-collection")) {
					var collection = collections.get(val);
					if (collection == null)
						log.warn(String.format("No collection named %s", val));
					else {
						List<Node> newCollectionNodes = new ArrayList<>();
						for (String elVal : collection) {
							Node template = node.cloneNode(false);
							template.setTextContent(elVal);
							newCollectionNodes.add(template);
						}
						newNodes.put(node.getParentNode(), newCollectionNodes);
						removeNodes.add(node);
					}
				} else if (attr.getNodeName().equals("data-content")) {
					node.setTextContent(replacements.get(val));
				}
			}
		}

		var n = node.getChildNodes();
		for (int i = 0; i < n.getLength(); i++) {
			var c = n.item(i);
			if (c instanceof Element el)
				dataAttributes(el, newNodes, removeNodes);
		}
	}
}
