package com.logonbox.vpn.client.gui.swt;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.Util;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

/**
 * This object is exposed to the local HTML/Javascript that runs in the browser.
 */
public class ServerBridge {
	final static Logger LOG = LoggerFactory.getLogger(ServerBridge.class);

	/**
	 * 
	 */
	private final UI ui;

	public ServerBridge(UI ui) {
		this.ui = ui;
	}

	public void addConnection(JsonObject rootObj) throws URISyntaxException, IOException {

		String configurationFile = rootObj.has("configurationFile") ? rootObj.get("configurationFile").getAsString()
				: null;
		if (configurationFile.equals("")) {

			Boolean connectAtStartup = rootObj.has("connectAtStartup") ? rootObj.get("connectAtStartup").getAsBoolean()
					: null;
			Boolean stayConnected = rootObj.has("stayConnected") ? rootObj.get("stayConnected").getAsBoolean() : null;
			String server = rootObj.has("serverUrl") ? rootObj.get("serverUrl").getAsString() : null;
			if (StringUtils.isBlank(server))
				throw new IllegalArgumentException(UI.bundle.getString("error.invalidUri"));

			Mode mode = rootObj.has("mode") ? Mode.valueOf(rootObj.get("mode").getAsString()) : Mode.CLIENT;
			this.ui.addConnection(stayConnected, connectAtStartup, server, mode);
		} else {
			this.ui.importConnection(Paths.get(configurationFile));
		}
	}

	public void setAsFavourite(long id) {
		this.ui.setAsFavourite(this.ui.context.getDBus().getVPNConnection(id));
	}

	public void confirmDelete(long id) {
		this.ui.confirmDelete(this.ui.context.getDBus().getVPNConnection(id));
	}

	public String browse() {
		var fileChooser = new FileDialog(this.ui.getShell(), SWT.OPEN);
		var cfg = Configuration.getDefault();
		var cfgDir = cfg.configurationFileProperty();
		fileChooser.setFileName(cfgDir);
		fileChooser.setText(UI.bundle.getString("chooseConfigurationFile"));
		var filename = fileChooser.open();
		if (filename == null)
			return "";
		else {
			var file = new File(filename);
			cfg.configurationFileProperty(file.getAbsolutePath());
			return filename == null ? "" : file.getAbsolutePath();
		}
	}

	public void configure(String usernameHint, String configIniFile) {
		if (UI.LOG.isDebugEnabled())
			UI.LOG.debug(String.format("Connect user: %s, Config: %s", usernameHint, configIniFile));
		this.ui.configure(usernameHint, configIniFile, this.ui.getForegroundConnection());
	}

	public void reset() {
		this.ui.selectPageForState(false, true);
	}

	public void details(long id) {
		this.ui.setHtmlPage("details.html#" + id);
	}

	public void edit(long id) {
		this.ui.editConnection(this.ui.context.getDBus().getVPNConnection(id));
	}

	public void connectTo(long id) {
		this.ui.connect(this.ui.context.getDBus().getVPNConnection(id));
	}

	public void disconnectFrom(long id) {
		this.ui.disconnect(this.ui.context.getDBus().getVPNConnection(id));
	}

	public void editConnection(JsonObject rootObj) {
		Boolean connectAtStartup = rootObj.has("connectAtStartup") ? rootObj.get("connectAtStartup").getAsBoolean()
				: null;
		Boolean stayConnected = rootObj.has("stayConnected") ? rootObj.get("stayConnected").getAsBoolean() : null;
		String server = rootObj.has("serverUrl") ? rootObj.get("serverUrl").getAsString() : null;
		String name = rootObj.has("name") ? rootObj.get("name").getAsString() : null;
		this.ui.editConnection(connectAtStartup, stayConnected, name, server, ui.getForegroundConnection());
	}

	public VPNConnection getConnection() {
		return this.ui.getForegroundConnection();
	}

	public String getOS() {
		return Util.getOS();
	}

	public VPN getVPN() {
		return this.ui.context.getDBus().getVPN();
	}

	public String getDeviceName() {
		return Util.getDeviceName();
	}

	public String getUserPublicKey() {
		VPNConnection connection = getConnection();
		return connection == null ? null : connection.getUserPublicKey();
	}

	public void log(String message) {
		UI.LOG.info("WEB: " + message);
	}

	public void reload() {
		this.ui.initUi();
	}

	public void saveOptions(JsonObject rootObj) {
		String trayMode = rootObj.has("trayMode") ? rootObj.get("trayMode").getAsString() : null;
		String darkMode = rootObj.has("darkMode") ? rootObj.get("darkMode").getAsString() : null;
		String logLevel = rootObj.has("logLevel") ? rootObj.get("logLevel").getAsString() : null;
		String dnsIntegrationMethod = rootObj.has("dnsIntegrationMethod")
				? rootObj.get("dnsIntegrationMethod").getAsString()
				: null;
		String phase = rootObj.has("phase") ? rootObj.get("phase").getAsString() : null;
		Boolean automaticUpdates = rootObj.has("automaticUpdates") ? rootObj.get("automaticUpdates").getAsBoolean()
				: null;
		Boolean ignoreLocalRoutes = rootObj.has("ignoreLocalRoutes") ? rootObj.get("ignoreLocalRoutes").getAsBoolean()
				: null;
		Boolean saveCookies = rootObj.has("saveCookies") ? rootObj.get("saveCookies").getAsBoolean() : null;
		this.ui.saveOptions(trayMode, darkMode, phase, automaticUpdates, logLevel, ignoreLocalRoutes,
				dnsIntegrationMethod, saveCookies);
	}

	public void showError(String error) {
		this.ui.showError(error);
	}

	public void unjoinAll(String reason) {
		this.ui.disconnect(null, reason);
	}

	public void update() {
		this.ui.update();
	}

	public void deferUpdate() {
		this.ui.deferUpdate();
	}

	public void checkForUpdate() {
		this.ui.checkForUpdate();
	}

	public void openURL(String url) {
		try {
			this.ui.context.openURL(url);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to open URL.", e);
		}
	}

	public void page(String url) {
		LOG.info("Javascript page change to {}", url);
		this.ui.getDisplay().asyncExec(() -> ui.setHtmlPage(url));
	}

	public String getLastHandshake() {
		VPNConnection connection = getConnection();
		return connection == null ? null
				: DateFormat.getDateTimeInstance().format(new Date(connection.getLastHandshake()));
	}

	public String getUsage() {
		VPNConnection connection = getConnection();
		return connection == null ? null
				: MessageFormat.format(UI.BUNDLE.getString("usageDetail"), Util.toHumanSize(connection.getRx()),
						Util.toHumanSize(connection.getTx()));
	}

	public VPNConnection[] getConnections() {
		var l = ui.getAllConnections();
		var f = ui.getFavouriteConnection(l);
		if (f != null) {
			l.remove(f);
			l.add(0, f);
		}
		return l.toArray(new VPNConnection[0]);
	}
}