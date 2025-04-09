package com.logonbox.vpn.client.gui.swt;

import static com.logonbox.vpn.client.common.Utils.isBlank;

import com.google.gson.JsonObject;
import com.jadaptive.nodal.core.lib.util.OsUtil;
import com.jadaptive.nodal.core.lib.util.Util;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.dbus.VpnConnection;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;

/**
 * This object is exposed to the local HTML/Javascript that runs in the browser.
 */
public class ServerBridge2 {
	final static Logger LOG = LoggerFactory.getLogger(ServerBridge2.class);

	/**
	 * 
	 */
	private final UI2 ui;
    private final Client context;
    private final VpnManager<VpnConnection> vpnManager;

	public ServerBridge2(UI2 ui, Client context) {
		this.ui = ui;
		this.context = context;
		this.vpnManager = context.getApp().getVpnManager();
	}

	public void addConnection(JsonObject rootObj) throws URISyntaxException, IOException {

		String configurationFile = rootObj.has("configurationFile") ? rootObj.get("configurationFile").getAsString()
				: null;
		if (configurationFile.equals("")) {

			Boolean connectAtStartup = rootObj.has("connectAtStartup") ? rootObj.get("connectAtStartup").getAsBoolean()
					: null;
			Boolean stayConnected = rootObj.has("stayConnected") ? rootObj.get("stayConnected").getAsBoolean() : null;
			String server = rootObj.has("serverUrl") ? rootObj.get("serverUrl").getAsString() : null;
			if (isBlank(server))
				throw new IllegalArgumentException(UI.bundle.getString("error.invalidUri"));

			Mode mode = rootObj.has("mode") ? Mode.valueOf(rootObj.get("mode").getAsString()) : Mode.CLIENT;
			this.ui.addConnection(stayConnected, connectAtStartup, server, mode);
		} else {
			this.ui.importConnection(Paths.get(configurationFile));
		}
	}

	public void setAsFavourite(long id) {
		this.ui.setAsFavourite(vpnManager.getVpnOrFail().getConnection(id));
	}

	public void confirmDelete(long id) {
		this.ui.confirmDelete(vpnManager.getVpnOrFail().getConnection(id));
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
		this.ui.editConnection(vpnManager.getVpnOrFail().getConnection(id));
	}

	public void connectTo(long id) {
		this.ui.connect(vpnManager.getVpnOrFail().getConnection(id));
	}

	public void disconnectFrom(long id) {
		this.ui.disconnect(vpnManager.getVpnOrFail().getConnection(id));
	}

	public void editConnection(JsonObject rootObj) {
		Boolean connectAtStartup = rootObj.has("connectAtStartup") ? rootObj.get("connectAtStartup").getAsBoolean()
				: null;
		Boolean stayConnected = rootObj.has("stayConnected") ? rootObj.get("stayConnected").getAsBoolean() : null;
		String server = rootObj.has("serverUrl") ? rootObj.get("serverUrl").getAsString() : null;
		String name = rootObj.has("name") ? rootObj.get("name").getAsString() : null;
		this.ui.editConnection(connectAtStartup, stayConnected, name, server, ui.getForegroundConnection());
	}

	public IVpnConnection getConnection() {
		return this.ui.getForegroundConnection();
	}

	public String getOS() {
		return OsUtil.getOS();
	}

	public String getDeviceName() {
		return Util.getDeviceName();
	}

	public String getUserPublicKey() {
		var connection = getConnection();
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
			this.context.openURL(url);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to open URL.", e);
		}
	}

	public void page(String url) {
		LOG.info("Javascript page change to {}", url);
		this.ui.getDisplay().asyncExec(() -> ui.setHtmlPage(url));
	}

	public String getLastHandshake() {
		var connection = getConnection();
		return connection == null ? null
				: DateFormat.getDateTimeInstance().format(new Date(connection.getLastHandshake()));
	}

	public String getUsage() {
		var connection = getConnection();
		return connection == null ? null
				: MessageFormat.format(UI.BUNDLE.getString("usageDetail"), Util.toHumanSize(connection.getRx()),
						Util.toHumanSize(connection.getTx()));
	}

	public IVpnConnection[] getConnections() {
		var l = ui.getAllConnections();
		var f = ui.getFavouriteConnection(l);
		if (f != null) {
			l.remove(f);
			l.add(0, f);
		}
		return l.toArray(new IVpnConnection[0]);
	}
}