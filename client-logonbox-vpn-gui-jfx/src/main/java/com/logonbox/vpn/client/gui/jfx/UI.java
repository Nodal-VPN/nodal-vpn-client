package com.logonbox.vpn.client.gui.jfx;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.CookieHandler;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.net.ssl.HostnameVerifier;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.messages.DBusSignal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.json.AuthenticationRequiredResult;
import com.hypersocket.json.AuthenticationResult;
import com.hypersocket.json.input.InputField;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.AbstractDBusClient.BusLifecycleListener;
import com.logonbox.vpn.common.client.AuthenticationCancelledException;
import com.logonbox.vpn.common.client.ConfigurationItem;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.logonbox.vpn.common.client.HypersocketVersion;
import com.logonbox.vpn.common.client.ServiceClient;
import com.logonbox.vpn.common.client.ServiceClient.NameValuePair;
import com.logonbox.vpn.common.client.UpdateService;
import com.logonbox.vpn.common.client.Util;
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;
import com.sshtools.twoslices.Slice;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;
//import com.sun.javafx.util.Utils;
import com.vladsch.javafx.webview.debugger.DevToolsDebuggerJsBridge;
import com.vladsch.javafx.webview.debugger.JfxScriptStateProvider;

import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;

public class UI implements BusLifecycleListener {

	private static final int SPLASH_HEIGHT = 360;
	private static final int SPLASH_WIDTH = 480;

	private static final String DEFAULT_LOCALHOST_ADDR = "http://localhost:59999/";

	public static final class ServiceClientAuthenticator implements ServiceClient.Authenticator {
		private final WebEngine engine;
		private final Semaphore authorizedLock;
		private final Client context;
		private Map<InputField, NameValuePair> results;
		private AuthenticationRequiredResult result;
		private boolean error;

		public ServiceClientAuthenticator(Client context, WebEngine engine, Semaphore authorizedLock) {
			this.engine = engine;
			this.context = context;
			this.authorizedLock = authorizedLock;
		}

		public void cancel() {
			log.info("Cancelling authorization.");
			error = true;
			authorizedLock.release();
		}

		public void submit(JSObject obj) {
			for (InputField field : result.getFormTemplate().getInputFields()) {
				results.put(field, new NameValuePair(field.getResourceKey(),
						memberOrDefault(obj, field.getResourceKey(), String.class, "")));
			}
			authorizedLock.release();
		}

		@Override
		public String getUUID() {
			return context.getDBus().getVPN().getUUID();
		}

		@Override
		public HostnameVerifier getHostnameVerifier() {
			return Main.getInstance().getCertManager();
		}

		@Override
		public void error(JsonNode i18n, AuthenticationResult logonResult) {
			maybeRunLater(() -> {
				if (logonResult.isLastErrorIsResourceKey())
					engine.executeScript("authorizationError('"
							+ StringEscapeUtils.escapeEcmaScript(i18n.get(logonResult.getErrorMsg()).asText()) + "');");
				else
					engine.executeScript("authorizationError('"
							+ StringEscapeUtils.escapeEcmaScript(logonResult.getErrorMsg()) + "');");

			});
		}

		@Override
		public void collect(JsonNode i18n, AuthenticationRequiredResult result, Map<InputField, NameValuePair> results)
				throws IOException {
			this.results = results;
			this.result = result;

			maybeRunLater(() -> {
				JSObject jsobj = (JSObject) engine.executeScript("window");
				jsobj.setMember("remoteBundle", i18n);
				jsobj.setMember("result", result);
				jsobj.setMember("results", results);
				engine.executeScript("collect();");
			});
			try {
				log.info("Waiting for authorize semaphore to be released.");
				authorizedLock.acquire();
			} catch (InterruptedException e) {
				// TODO:
			}

			log.info("Left authorization.");
			if (error)
				throw new AuthenticationCancelledException();
		}

		@Override
		public void authorized() throws IOException {
			log.info("Authorized.");
			maybeRunLater(() -> {
				engine.executeScript("authorized();");
				authorizedLock.release();
			});
		}
	}

	public static final class Register implements Runnable {
		private final VPNConnection selectedConnection;
		private final ServiceClient serviceClient;
		private final UI ui;

		public Register(VPNConnection selectedConnection, ServiceClient serviceClient, UI ui) {
			this.selectedConnection = selectedConnection;
			this.serviceClient = serviceClient;
			this.ui = ui;
		}

		public void run() {
			new Thread() {
				public void run() {
					try {
						serviceClient.register(selectedConnection);
					} catch (AuthenticationCancelledException ae) {
						// Ignore, handled elsewhere
					} catch (IOException | URISyntaxException e) {
						maybeRunLater(() -> ui.showError("Failed to register.", e));
					}
				}
			}.start();
		}
	}

	/**
	 * This object is exposed to the local HTML/Javascript that runs in the browser.
	 */
	public class ServerBridge {

		public void addConnection(JSObject o) throws URISyntaxException, IOException {
			String configurationFile = memberOrDefault(o, "configurationFile", String.class, "");
			if(configurationFile.equals("")) {
				Boolean connectAtStartup = (Boolean) o.getMember("connectAtStartup");
				Boolean stayConnected = (Boolean) o.getMember("stayConnected");
				String server = (String) o.getMember("serverUrl");
				if (StringUtils.isBlank(server))
					throw new IllegalArgumentException(bundle.getString("error.invalidUri"));
				UI.this.addConnection(stayConnected, connectAtStartup, server);
			}
			else {
				UI.this.importConnection(Paths.get(configurationFile));
			}
		}

		public void setAsFavourite(long id) {
			UI.this.setAsFavourite(context.getDBus().getVPNConnection(id));
		}

		public void confirmDelete(long id) {
			UI.this.confirmDelete(context.getDBus().getVPNConnection(id));
		}
		
		public String browse() {
			var fileChooser = new FileChooser();
			var cfgDir = Configuration.getDefault().configurationFileProperty();
			fileChooser.setInitialDirectory(new File(cfgDir.get()));
			fileChooser.setTitle(bundle.getString("chooseConfigurationFile"));
			var file = fileChooser.showOpenDialog(getStage());
			if(file == null)
				return "";
			else {
				cfgDir.set(file.getParentFile().getAbsolutePath());
				return file == null ? "" : file.getAbsolutePath();
			}
		}

		public void configure(String usernameHint, String configIniFile) {
			if (LOG.isDebugEnabled())
				LOG.debug(String.format("Connect user: %s, Config: %s", usernameHint, configIniFile));
			UI.this.configure(usernameHint, configIniFile, UI.this.getForegroundConnection());
		}

		public void reset() {
			UI.this.selectPageForState(false, true);
		}

		public void details(long id) {
			setHtmlPage("details.html#" + id);
		}

		public void edit(long id) {
			UI.this.editConnection(context.getDBus().getVPNConnection(id));
		}

		public void connectTo(long id) {
			UI.this.connect(context.getDBus().getVPNConnection(id));
		}

		public void disconnectFrom(long id) {
			UI.this.disconnect(context.getDBus().getVPNConnection(id));
		}

		public void editConnection(JSObject o) {
			Boolean connectAtStartup = (Boolean) o.getMember("connectAtStartup");
			Boolean stayConnected = (Boolean) o.getMember("stayConnected");
			String server = (String) o.getMember("serverUrl");
			String name = (String) o.getMember("name");
			UI.this.editConnection(connectAtStartup, stayConnected, name, server, getForegroundConnection());
		}

		public VPNConnection getConnection() {
			return UI.this.getForegroundConnection();
		}

		public String getOS() {
			return Util.getOS();
		}

		public String getDeviceName() {
			return Util.getDeviceName();
		}

		public String getUserPublicKey() {
			VPNConnection connection = getConnection();
			return connection == null ? null : connection.getUserPublicKey();
		}

		public void log(String message) {
			LOG.info("WEB: " + message);
		}

		public void reload() {
			UI.this.initUi();
		}

		public void saveOptions(JSObject o) {
			String trayMode = memberOrDefault(o, "trayMode", String.class, null);
			String darkMode = memberOrDefault(o, "darkMode", String.class, null);
			String logLevel = memberOrDefault(o, "logLevel", String.class, null);
			String dnsIntegrationMethod = memberOrDefault(o, "dnsIntegrationMethod", String.class, null);
			String phase = memberOrDefault(o, "phase", String.class, null);
			Boolean automaticUpdates = memberOrDefault(o, "automaticUpdates", Boolean.class, null);
			Boolean ignoreLocalRoutes = memberOrDefault(o, "ignoreLocalRoutes", Boolean.class, null);
			Boolean saveCookies = memberOrDefault(o, "saveCookies", Boolean.class, null);
			UI.this.saveOptions(trayMode, darkMode, phase, automaticUpdates, logLevel, ignoreLocalRoutes,
					dnsIntegrationMethod, saveCookies);
		}

		public void showError(String error) {
			UI.this.showError(error);
		}

		public void unjoinAll(String reason) {
			UI.this.disconnect(null, reason);
		}

		public void update() {
			UI.this.update();
		}

		public void deferUpdate() {
			UI.this.deferUpdate();
		}

		public void checkForUpdate() {
			UI.this.checkForUpdate();
		}

		public void openURL(String url) {
			Client.get().getHostServices().showDocument(url);
		}

		public String getLastHandshake() {
			VPNConnection connection = getConnection();
			return connection == null ? null
					: DateFormat.getDateTimeInstance().format(new Date(connection.getLastHandshake()));
		}

		public String getUsage() {
			VPNConnection connection = getConnection();
			return connection == null ? null
					: MessageFormat.format(resources.getString("usageDetail"), Util.toHumanSize(connection.getRx()),
							Util.toHumanSize(connection.getTx()));
		}

		public VPNConnection[] getConnections() {
			var l = getAllConnections();
			var f = getFavouriteConnection(l);
			if(f != null) {
				l.remove(f);
				l.add(0, f);
			}
			return l.toArray(new VPNConnection[0]);
		}
	}

	class BrandingCacheItem {
		Branding branding;
		long loaded = System.currentTimeMillis();

		BrandingCacheItem(Branding branding) {
			this.branding = branding;
		}

		boolean isExpired() {
			return System.currentTimeMillis() > loaded + TimeUnit.MINUTES.toMillis(10);
		}
	}

	final static ResourceBundle bundle = ResourceBundle.getBundle(UI.class.getName());

	@SuppressWarnings("unchecked")
	static <T> T memberOrDefault(JSObject obj, String member, Class<T> clazz, T def) {
		try {
			Object o = obj.getMember(member);
			if ("undefined".equals(o)) {
				/*
				 * NOTE: Bit crap, means you can never actually have a value of "undefined", OK
				 * for our case, but could cause future problems.
				 */
				throw new Exception(member + " not defined.");
			}
			if (o == null) {
				return null;
			}
			return clazz.isAssignableFrom(o.getClass()) ? (T) o : def;
		} catch (Exception | Error ex) {
			return def;
		}
	}

	static int DROP_SHADOW_SIZE = 11;

	final static Logger log = LoggerFactory.getLogger(UI.class);

	private static Logger LOG = LoggerFactory.getLogger(UI.class);

	private static UI instance;

	public static UI getInstance() {
		return instance;
	}

	private Timeline awaitingBridgeEstablish;
	private Timeline awaitingBridgeLoss;
	private Branding branding;
	private Map<String, Collection<String>> collections = new HashMap<>();
	private Map<VPNConnection, BrandingCacheItem> brandingCache = new HashMap<>();
	private List<VPNConnection> connecting = new ArrayList<>();
	private String htmlPage;
	private String lastErrorMessage;
	private String lastErrorCause;
	private String lastException;
	private ResourceBundle pageBundle;
	private Path logoFile;
	private Runnable runOnNextLoad;
	private ServerBridge bridge;
	private String disconnectionReason;
	private DevToolsDebuggerJsBridge myJSBridge;
	private final JfxScriptStateProvider myStateProvider;
	private Map<Long, Map<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>>> eventsMap = new HashMap<>();
	private Map<Long, Slice> notificationsForConnections = new HashMap<>();

	@FXML
	private Label messageIcon;
	@FXML
	private Label messageText;
	@FXML
	private Hyperlink options;
	@FXML
	private VBox root;
	@FXML
	private WebView webView;
	@FXML
	private AnchorPane loading;
	@FXML
	private FontIcon loadingSpinner;
	@FXML
	private ImageView titleBarImageView;
	@FXML
	private Hyperlink close;
	@FXML
	private Hyperlink minimize;
	@FXML
	private Hyperlink back;
	@FXML
	private Parent debugBar;
	@FXML
	private Hyperlink debuggerLink;
	@FXML
	private Button startDebugger;
	@FXML
	private Button stopDebugger;
	@FXML
	private HBox titleLeft;
	@FXML
	private HBox titleRight;

	private UpdateService updateService;

	public UI() {
		/* TODO sort out all this static crap */
		instance = this;
		myStateProvider = Client.get();
		updateService = Main.getInstance().getUpdateService();
	}

	protected Client context;
	protected ResourceBundle resources;
	protected URL location;
	protected Scene scene;
	private RotateTransition loadingRotation;

	public final void initialize(URL location, ResourceBundle resources) {
		this.location = location;
		this.resources = resources;
		Font.loadFont(UI.class.getResource("ARLRDBD.TTF").toExternalForm(), 12);
	}

	public final void cleanUp() {
	}

	public final void configure(Scene scene, Client context) {
		this.scene = scene;
		this.context = context;
		onConfigure();
	}

	public Stage getStage() {
		return (Stage) scene.getWindow();
	}

	public Scene getScene() {
		return scene;
	}

	public void disconnect(VPNConnection sel) {
		disconnect(sel, null);
	}

	public void disconnect(VPNConnection sel, String reason) {
		if (sel == null)
			throw new IllegalArgumentException("No connection given.");
		if (reason == null)
			LOG.info("Requesting disconnect, no reason given");
		else
			LOG.info(String.format("Requesting disconnect, because '%s'", reason));

		context.getOpQueue().execute(() -> {
			try {
				sel.disconnect(StringUtils.defaultIfBlank(reason, ""));
			} catch (Exception e) {
				Platform.runLater(() -> showError("Failed to disconnect.", e));
			}
		});
	}

	public void connect(VPNConnection n) {
		try {
			if (n == null) {
				addConnection();
			} else {
				log.info(String.format("Connect to %s", n.getAddress()));
				if (n != null) {
					clearNotificationForConnection(n.getId());
					Type status = Type.valueOf(n.getStatus());
					log.info(String.format("  current status is %s", status));
					if (status == Type.CONNECTED || status == Type.CONNECTING || status == Type.DISCONNECTING)
						selectPageForState(false, false);
					else if (n.isAuthorized())
						joinNetwork(n);
					else
						authorize(n);
				}
			}
		} catch (Exception e) {
			showError("Failed to connect.", e);
		}
	}

	public void notify(Long id, String msg, ToastType toastType) {
		clearNotificationForConnection(id);
		putNotificationForConnection(id, Toast.toast(toastType, resources.getString("appName"), msg));
	}

	private void putNotificationForConnection(Long id, Slice slice) {
		if (id != null) {
			notificationsForConnections.put(id, slice);
		}
	}

	private void clearNotificationForConnection(Long id) {
		if (id != null) {
			Slice slice = notificationsForConnections.remove(id);
			if (slice != null) {
				try {
					slice.close();
				} catch (Exception e) {
				}
			}
		}
	}

	private Map<String, Object> beansForOptions() {
		Map<String, Object> beans = new HashMap<>();
		AbstractDBusClient dbus = context.getDBus();
		VPN vpn = dbus.isBusAvailable() ? dbus.getVPN() : null;
		if (vpn == null) {
			beans.put("phases", new String[0]);
			beans.put("phase", "");
			beans.put("automaticUpdates", "true");
			beans.put("ignoreLocalRoutes", "true");
			beans.put("dnsIntegrationMethod", DNSIntegrationMethod.AUTO.name());
		} else {
			try {
				beans.put("phases", updateService.getPhases());
			} catch (Exception e) {
				log.warn("Could not get phases.", e);
			}

			/* Configuration stored globally in service */
			beans.put("phase", vpn.getValue(ConfigurationItem.PHASE.getKey()));
			beans.put("dnsIntegrationMethod", vpn.getValue(ConfigurationItem.DNS_INTEGRATION_METHOD.getKey()));

			/* Store locally in preference */
			beans.put("automaticUpdates", vpn.getBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey()));
			beans.put("ignoreLocalRoutes", vpn.getBooleanValue(ConfigurationItem.IGNORE_LOCAL_ROUTES.getKey()));
		}

		/* Option collections */
		beans.put("trayModes", new String[] { Configuration.TRAY_MODE_AUTO, Configuration.TRAY_MODE_COLOR,
				Configuration.TRAY_MODE_DARK, Configuration.TRAY_MODE_LIGHT, Configuration.TRAY_MODE_OFF });
		beans.put("darkModes", new String[] { Configuration.DARK_MODE_AUTO, Configuration.DARK_MODE_ALWAYS,
				Configuration.DARK_MODE_NEVER });
		beans.put("logLevels",
				new String[] { "", org.apache.log4j.Level.ALL.toString(), org.apache.log4j.Level.TRACE.toString(),
						org.apache.log4j.Level.DEBUG.toString(), org.apache.log4j.Level.INFO.toString(),
						org.apache.log4j.Level.WARN.toString(), org.apache.log4j.Level.ERROR.toString(),
						org.apache.log4j.Level.FATAL.toString(), org.apache.log4j.Level.OFF.toString() });
		beans.put("dnsIntegrationMethods", Arrays.asList(DNSIntegrationMethod.valuesForOs()).stream()
				.map(DNSIntegrationMethod::name).collect(Collectors.toUnmodifiableList()).toArray(new String[0]));

		/* Per-user GUI specific */
		Configuration config = Configuration.getDefault();
		beans.put("trayMode", config.trayModeProperty().get());
		beans.put("darkMode", config.darkModeProperty().get());
		beans.put("logLevel", config.logLevelProperty().get() == null ? "" : config.logLevelProperty().get());
		beans.put("saveCookies", config.saveCookiesProperty().get());

		return beans;
	}

	public void options() {
		setHtmlPage("options.html");
	}

	public void refresh() {
		selectPageForState(false, true);
	}

	public void reload() {
//		webView.getEngine().reload();
		/* TODO: hrm, why cant we just refresh the page? */
		selectPageForState(false, true);
	}

	@Override
	public void busInitializer(DBusConnection connection) {
		LOG.info("Bridge established.");

		try {
			connection.addSigHandler(VPN.ConnectionAdded.class, context.getDBus().getVPN(),
					new DBusSigHandler<VPN.ConnectionAdded>() {
						@Override
						public void handle(VPN.ConnectionAdded sig) {
							maybeRunLater(() -> {
								try {
									VPNConnection addedConnection = context.getDBus().getVPNConnection(sig.getId());
									try {
										listenConnectionEvents(connection, addedConnection);
									} catch (DBusException e) {
										throw new IllegalStateException("Failed to listen for new connections events.");
									}

									context.getOpQueue().execute(() -> {
										reloadState(() -> {
											maybeRunLater(() -> {
												reapplyColors();
												reapplyLogo();
												if(addedConnection.isAuthorized())
													joinNetwork(addedConnection);
												else
													authorize(addedConnection);
											});
										});
									});
								} finally {
									log.info("Connection added");
								}
							});
						}
					});
			connection.addSigHandler(VPN.ConnectionRemoved.class, context.getDBus().getVPN(),
					new DBusSigHandler<VPN.ConnectionRemoved>() {
						@Override
						public void handle(VPN.ConnectionRemoved sig) {
							try {
								removeConnectionEvents(connection, sig.getId());
							} catch (DBusException e) {
							}
							maybeRunLater(() -> {
								try {
									context.getOpQueue().execute(() -> {
										reloadState(() -> {
											maybeRunLater(() -> {
												reapplyColors();
												reapplyLogo();
												selectPageForState(false, false);
											});
										});
									});
								} finally {
									log.info("Connection deleted");
								}
							});
						}
					});
			connection.addSigHandler(VPN.Exit.class, context.getDBus().getVPN(), new DBusSigHandler<VPN.Exit>() {
				@Override
				public void handle(VPN.Exit sig) {
					context.exitApp();
				}
			});
			connection.addSigHandler(VPN.ConnectionUpdated.class, context.getDBus().getVPN(),
					new DBusSigHandler<VPN.ConnectionUpdated>() {
						@Override
						public void handle(VPN.ConnectionUpdated sig) {
							maybeRunLater(() -> {
								selectPageForState(false, false);
							});
						}
					});

			connection.addSigHandler(VPN.GlobalConfigChange.class, context.getDBus().getVPN(),
					new DBusSigHandler<VPN.GlobalConfigChange>() {
						@Override
						public void handle(VPN.GlobalConfigChange sig) {
							if (sig.getName().equals(ConfigurationItem.FAVOURITE.getKey())) {
								reloadState(() -> {
									maybeRunLater(() -> {
										initUi();
										refresh();
									});
								});
							}
						}
					});

			/* Listen for events on all existing connections */
			for (VPNConnection vpnConnection : context.getDBus().getVPNConnections()) {
				listenConnectionEvents(connection, vpnConnection);
			}

		} catch (DBusException dbe) {
			throw new IllegalStateException("Failed to configure.", dbe);
		}

		Runnable runnable = new Runnable() {
			public void run() {
				if (awaitingBridgeEstablish != null) {
					// Bridge established as result of update, now restart the
					// client itself
					resetAwaingBridgeEstablish();
					setUpdateProgress(100, resources.getString("guiRestart"));
					new Timeline(new KeyFrame(Duration.seconds(5), ae -> Main.getInstance().restart())).play();
				} else {
					String unprocessedUri = Main.getInstance().getUri();
					if (StringUtils.isNotBlank(unprocessedUri)) {
						connectToUri(unprocessedUri);
					} else {
						initUi();
						selectPageForState(Main.getInstance().isConnect(), false);
					}
				}
			}
		};

		/*
		 * If we were waiting for this, it's part of the update process. We don't want
		 * the connection continuing
		 */
		if (awaitingBridgeEstablish != null) {
			maybeRunLater(runnable);
		} else {
			reloadState(() -> maybeRunLater(runnable));
		}
	}

	@SuppressWarnings("unchecked")
	protected void removeConnectionEvents(DBusConnection bus, Long id) throws DBusException {
		Map<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>> map = eventsMap.remove(id);
		if (map != null) {
			for (Map.Entry<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>> en : map.entrySet()) {
				bus.removeSigHandler((Class<DBusSignal>) en.getKey(), (DBusSigHandler<DBusSignal>) en.getValue());
			}
		}
	}

	protected <S extends DBusSignal, H extends DBusSigHandler<S>> H registerMappedEvent(VPNConnection connection,
			Class<S> clazz, H handler) {
		Map<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>> map = eventsMap.get(connection.getId());
		if (map == null) {
			map = new HashMap<>();
			eventsMap.put(connection.getId(), map);
		}
		map.put(clazz, handler);
		return handler;
	}

	protected void listenConnectionEvents(DBusConnection bus, VPNConnection connection) throws DBusException {
		var id = connection.getId();
		/*
		 * Client needs authorizing (first time configuration needed, or connection
		 * failed with existing configuration)
		 */
		bus.addSigHandler(VPNConnection.Authorize.class, registerMappedEvent(connection, VPNConnection.Authorize.class,
				new DBusSigHandler<VPNConnection.Authorize>() {
					@Override
					public void handle(VPNConnection.Authorize sig) {
						if (sig.getId() != id)
							return;
						disconnectionReason = null;
						Mode authMode = Connection.Mode.valueOf(sig.getMode());
						if (authMode.equals(Connection.Mode.CLIENT) || authMode.equals(Connection.Mode.SERVICE)) {
							maybeRunLater(() -> {
								// setHtmlPage(connection.getUri(false) + sig.getUri());
								selectPageForState(false, false);
							});
						} else {
							LOG.info(String.format("This client doest not handle the authorization mode '%s'",
									sig.getMode()));
						}
					}
				}));
		bus.addSigHandler(VPNConnection.Connecting.class, registerMappedEvent(connection,
				VPNConnection.Connecting.class, new DBusSigHandler<VPNConnection.Connecting>() {
					@Override
					public void handle(VPNConnection.Connecting sig) {
						if (sig.getId() != id)
							return;
						disconnectionReason = null;
						maybeRunLater(() -> {
							clearNotificationForConnection(sig.getId());
							selectPageForState(false, false);
						});
					}
				}));
		bus.addSigHandler(VPNConnection.Connected.class, registerMappedEvent(connection, VPNConnection.Connected.class,
				new DBusSigHandler<VPNConnection.Connected>() {
					@Override
					public void handle(VPNConnection.Connected sig) {
						VPNConnection connection = context.getDBus().getVPNConnection(sig.getId());
						if (sig.getId() != id)
							return;
						maybeRunLater(() -> {
							UI.this.notify(sig.getId(), MessageFormat.format(bundle.getString("connected"),
									connection.getDisplayName(), connection.getHostname()), ToastType.INFO);
							connecting.remove(connection);
							if (Main.getInstance().isExitOnConnection()) {
								context.exitApp();
							} else
								selectPageForState(false, true);
						});
					}
				}));

		/* Failed to connect */
		bus.addSigHandler(VPNConnection.Failed.class,
				registerMappedEvent(connection, VPNConnection.Failed.class, new DBusSigHandler<VPNConnection.Failed>() {
					@Override
					public void handle(VPNConnection.Failed sig) {
						if (sig.getId() != id)
							return;
						VPNConnection connection = context.getDBus().getVPNConnection(sig.getId());
						maybeRunLater(() -> {
							String s = sig.getReason();
							log.info(String.format("Failed to connect. %s", s));
							connecting.remove(connection);
							showError("Failed to connect.", s, sig.getTrace());
							UI.this.notify(sig.getId(), s, ToastType.ERROR);
							uiRefresh();
						});
					}
				}));

		/* Temporarily offline */
		bus.addSigHandler(VPNConnection.TemporarilyOffline.class, registerMappedEvent(connection,
				VPNConnection.TemporarilyOffline.class, new DBusSigHandler<VPNConnection.TemporarilyOffline>() {
					@Override
					public void handle(VPNConnection.TemporarilyOffline sig) {
						if (sig.getId() != id)
							return;
						disconnectionReason = sig.getReason();
						maybeRunLater(() -> {
							clearNotificationForConnection(sig.getId());
							log.info("Temporarily offline " + sig.getId());
							selectPageForState(false, false);
						});
					}
				}));

		/* Disconnected */
		bus.addSigHandler(VPNConnection.Disconnected.class, registerMappedEvent(connection,
				VPNConnection.Disconnected.class, new DBusSigHandler<VPNConnection.Disconnected>() {
					@Override
					public void handle(VPNConnection.Disconnected sig) {
						if (sig.getId() != id)
							return;
						String thisDisconnectionReason = sig.getReason();
						maybeRunLater(() -> {
							log.info("Disconnected " + sig.getId());
							try {
								/*
								 * WARN: Do not try to get a connection object directly here. The disconnection
								 * event may be the result of a deletion, so the connection won't exist any
								 * more. Use only what is available in the signal object. If you need more
								 * detail, add it to that.
								 */
								clearNotificationForConnection(sig.getId());
								if (StringUtils.isBlank(thisDisconnectionReason)
										|| bundle.getString("cancelled").equals(thisDisconnectionReason))
									putNotificationForConnection(sig.getId(), Toast.builder()
											.title(resources.getString("appName"))
											.content(MessageFormat.format(bundle.getString("disconnectedNoReason"),
													sig.getDisplayName(), sig.getHostname()))
											.type(ToastType.INFO).defaultAction(() -> Client.get().open()).toast());
								else
									putNotificationForConnection(sig.getId(), Toast.builder()
											.title(resources.getString("appName"))
											.content(MessageFormat.format(bundle.getString("disconnected"),
													sig.getDisplayName(), sig.getHostname(), thisDisconnectionReason))
											.type(ToastType.INFO).defaultAction(() -> Client.get().open())
											.action(bundle.getString("reconnect"), () -> {
												Client.get().open();
												UI.this.connect(context.getDBus().getVPNConnection(sig.getId()));
											}).timeout(0).toast());
							} catch (Exception e) {
								log.error("Failed to get connection, delete not possible.", e);
							} finally {
								selectPageForState(false, false);
							}

						});
					}
				}));

		/* Disconnecting event */
		bus.addSigHandler(VPNConnection.Disconnecting.class, registerMappedEvent(connection,
				VPNConnection.Disconnecting.class, new DBusSigHandler<VPNConnection.Disconnecting>() {
					@Override
					public void handle(VPNConnection.Disconnecting sig) {
						if (sig.getId() != id)
							return;
						maybeRunLater(() -> {
							clearNotificationForConnection(sig.getId());
							log.info("Disconnecting " + bus /* + " (delete " + deleteOnDisconnect + ")" */);
							selectPageForState(false, false);
						});
					}
				}));
	}

	@Override
	public void busGone() {
		LOG.info("Bridge lost");
		maybeRunLater(() -> {
			if (awaitingBridgeLoss != null) {
				// Bridge lost as result of update, wait for it to come back
				resetAwaingBridgeLoss();
				setUpdateProgress(100, resources.getString("waitingStart"));
				awaitingBridgeEstablish = new Timeline(
						new KeyFrame(Duration.seconds(30), ae -> giveUpWaitingForBridgeEstablish()));
				awaitingBridgeEstablish.play();
			} else {
				connecting.clear();
				initUi();
				selectPageForState(false, false);
			}
		});
	}

	protected void joinNetwork(VPNConnection connection) {
		context.getOpQueue().execute(() -> {
			connection.connect();
		});
	}

	protected void addConnection(Boolean stayConnected, Boolean connectAtStartup, String unprocessedUri)
			throws URISyntaxException {
		URI uriObj = Util.getUri(unprocessedUri);
		context.getOpQueue().execute(() -> {
			try {
				context.getDBus().getVPN().createConnection(uriObj.toASCIIString(), connectAtStartup, stayConnected,
						Mode.CLIENT.name());
			} catch (Exception e) {
				showError("Failed to add connection.", e);
			}
		});
	}

	protected void importConnection(Path file)
			throws IOException {
		try(var r = Files.newBufferedReader(file)) {
			String content = IOUtils.toString(r);
			context.getOpQueue().execute(() -> {
				try {
					context.getDBus().getVPN().importConfiguration(content);
				} catch (Exception e) {
					showError("Failed to add connection.", e);
				}
			});	
		}
	}

	protected void changeHtmlPage(String htmlPage) {
		this.htmlPage = htmlPage;
		setAvailable();
	}

	protected void authorize(VPNConnection n) {
		context.getOpQueue().execute(() -> {
			try {
				n.authorize();
			} catch (Exception e) {
				showError("Failed to join VPN.", e);
			}
		});
	}

	protected void configureWebEngine() {
		WebEngine engine = webView.getEngine();
		webView.setContextMenuEnabled(context.isChromeDebug());
		String ua = engine.getUserAgent();
		log.info("User Agent: " + ua);
		engine.setUserAgent(ua + " " + "LogonBoxVPNClient/"
				+ HypersocketVersion.getVersion("com.logonbox/client-logonbox-vpn-gui-jfx"));
		engine.setOnAlert((e) -> {
			Alert alert = new Alert(AlertType.ERROR);
			alert.initModality(Modality.APPLICATION_MODAL);
			alert.initOwner(getStage());
			alert.setTitle("Alert");
			alert.setContentText(e.getData());
			alert.showAndWait();
		});
		engine.setOnError((e) -> {
			LOG.error(String.format("Error in webengine. %s", e.getMessage()), e.getException());
		});

		engine.setOnStatusChanged((e) -> {
			LOG.debug(String.format("Status: %s", e));
		});
		engine.locationProperty().addListener((c, oldLoc, newLoc) -> {
			if (newLoc != null) {
				if (isRemote(newLoc)) {
					log.info(String.format("This is a remote page (%s), not changing current html page", newLoc));
					changeHtmlPage(null);
				} else {
					String base = newLoc;
					int idx = newLoc.lastIndexOf('?');
					if (idx != -1) {
						base = newLoc.substring(0, idx);
					}
					idx = base.lastIndexOf('/');
					if (idx != -1) {
						base = base.substring(idx + 1);
					}
					if (base.equals(""))
						base = "index.html";
					idx = base.indexOf('#');
//					if (idx != -1) {
//						base = base.substring(0, idx);
//					}
					if (!base.equals("") && !base.equals(htmlPage)) {
						changeHtmlPage(base);
						log.debug(String.format("Page changed by user to %s (from browser view)", htmlPage));
					}
				}
				setupPage();
			}
		});
		engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
			if (newState == State.SUCCEEDED) {
				if (context.isChromeDebug()) {
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

				processDOM();
				processJavascript();
				setLoading(false);
				if (runOnNextLoad != null) {
					try {
						runOnNextLoad.run();
					} finally {
						runOnNextLoad = null;
					}
				}
			}
		});
		engine.getLoadWorker().exceptionProperty().addListener((o, old, value) -> {
			if (value == null) {
				/* Error cleared */
				return;
			}

			/*
			 * If we are authorizing and get an error from the browser, then it's likely the
			 * target server is offline and can't do the authorization.
			 * 
			 * So cancel the authorize and show the error and allow retry.
			 */
			VPNConnection priorityConnection = getPriorityConnection();
			try {
				if (priorityConnection != null && ConnectionStatus.Type
						.valueOf(priorityConnection.getStatus()) == ConnectionStatus.Type.AUTHORIZING) {
					String reason = value != null ? value.getMessage() : null;
					LOG.info(String.format("Got error while authorizing. Disconnecting now using '%s' as the reason",
							reason));
					context.getOpQueue().execute(() -> {
						try {
							priorityConnection.disconnect(reason);
						} catch (Exception e) {
							Platform.runLater(() -> showError("Failed to disconnect.", e));
						}
					});

					/*
					 * Await the disconnected event that comes back from the service and process it
					 * then
					 */
					return;
				}
			} catch (Exception e) {
				LOG.error("Failed to adjust authorizing state.", e);
			}
			if (!checkForInvalidatedSession(value)) {
				showError("Error.", value);
			}
		});

		engine.setJavaScriptEnabled(true);
	}

	protected boolean isRemote(String newLoc) {
		return (newLoc.startsWith("http://") || newLoc.startsWith("https://"))
				&& !(newLoc.startsWith(DEFAULT_LOCALHOST_ADDR));
	}

	protected boolean checkForInvalidatedSession(Throwable value) {
		/*
		 * This is pretty terrible. JavFX's WebEngine$LoadWorker constructs a new
		 * <strong>Throwable</strong>! with English text as the only way of
		 * distinguishing the actual error! This is ridiculous. Hopefully it will get
		 * fixed, but then this will probably break.
		 */
		if (value != null && value.getMessage() != null) {
			if (value.getMessage().equals("Connection refused by server")) {
				selectPageForState(false, false);
				return true;
			}
		}
		return false;
	}

	protected void editConnection(Boolean connectAtStartup, Boolean stayConnected, String name, String server,
			VPNConnection connection) {
		try {
			URI uriObj = Util.getUri(server);

			connection.setName(name);
			connection.setHostname(uriObj.getHost());
			connection
					.setPort(uriObj.getPort() < 1 ? (uriObj.getScheme().equals("https") ? 443 : 80) : uriObj.getPort());
			connection.setConnectAtStartup(connectAtStartup);
			connection.setStayConnected(stayConnected);
			if (!connection.isTransient())
				connection.save();

		} catch (Exception e) {
			showError("Failed to save connection.", e);
		}
	}

	protected VPNConnection getFavouriteConnection() {
		return getFavouriteConnection(getAllConnections());
	}

	protected VPNConnection getFavouriteConnection(Collection<VPNConnection> list) {
		var fav = list.isEmpty() ? 0l : context.getDBus().getVPN().getLongValue(ConfigurationItem.FAVOURITE.getKey());
		for (var connection : list) {
			if (list.size() == 1 || connection.getId() == fav)
				return connection;
		}
		return list.isEmpty() ? null : list.iterator().next();
	}

	protected VPNConnection getPriorityConnection() {
		List<VPNConnection> alls = getAllConnections();
		if (alls.isEmpty())
			return null;

		/*
		 * New UI, we use the connection that needs the most attention. If / when it
		 * becomes the default, then this will not be necessary.
		 */
		for (VPNConnection connection : alls) {
			ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
			if (type == Type.AUTHORIZING) {
				return connection;
			}
		}

		for (VPNConnection connection : alls) {
			ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
			if (type == Type.CONNECTING || type == Type.DISCONNECTING) {
				return connection;
			}
		}

		for (VPNConnection connection : alls) {
			ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
			if (type == Type.CONNECTED) {
				return connection;
			}
		}

		return alls.get(0);
	}

	protected void onConfigure() {

		/* If on Mac, swap which side minimize / close is on */
		if (SystemUtils.IS_OS_MAC_OSX) {
			var tempL = new ArrayList<>(titleLeft.getChildren());
			var tempR = new ArrayList<>(titleRight.getChildren());
			Collections.reverse(tempL);
			Collections.reverse(tempR);
			titleLeft.getChildren().clear();
			titleLeft.getChildren().addAll(tempR);
			titleRight.getChildren().clear();
			titleRight.getChildren().addAll(tempL);
		}

		bridge = new ServerBridge();

		// create JSBridge Instance
		myJSBridge = new DevToolsJsBridge(webView, 0, myStateProvider);

		setAvailable();

		/* Configure engine */
		configureWebEngine();

		/* Watch for update check state changing */
		Main.getInstance().getUpdateService().addListener(() -> {
			maybeRunLater(() -> {
				if (getAuthorizingConnection() == null)
					selectPageForState(false, false);
			});
		});

		// TEMP
//		webView.visibleProperty().set(false);
//		webView.managedProperty().set(false);
//		titleBarImageView.visibleProperty().set(false);
//		titleBarImageView.managedProperty().set(false);
//		sidebar.visibleProperty().set(false);
//		sidebar.managedProperty().set(false);

		/* Make various components completely hide from layout when made invisible */
		debugBar.managedProperty().bind(debugBar.visibleProperty());
		back.managedProperty().bind(back.visibleProperty());
		back.managedProperty().bind(back.visibleProperty());

		stopDebugger.disableProperty().bind(Bindings.not(startDebugger.disableProperty()));

		/* Initial page */
//		setHtmlPage("index.html");
		try {
			context.getDBus().addBusLifecycleListener(this);
			context.getDBus().getVPN().ping();
			selectPageForState(false, false);
		} catch (Exception e) {
			setHtmlPage("index.html");
		}
	}

	public void setAvailable() {
		boolean isNoBack = "missingSoftware.html".equals(htmlPage) || "connections.html".equals(htmlPage)
				|| !context.getDBus().isBusAvailable()
				|| ("addLogonBoxVPN.html".equals(htmlPage) && context.getDBus().getVPNConnections().isEmpty());
		back.visibleProperty().set(!isNoBack);
		debugBar.setVisible(context.isChromeDebug());
		minimize.setVisible(!Main.getInstance().isNoMinimize() && Client.get().isMinimizeAllowed()
				&& Client.get().isUndecoratedWindow());
		minimize.setManaged(minimize.isVisible());
		close.setVisible(!Main.getInstance().isNoClose() && Client.get().isUndecoratedWindow());
		close.setManaged(close.isVisible());
	}

	protected void saveOptions(String trayMode, String darkMode, String phase, Boolean automaticUpdates,
			String logLevel, Boolean ignoreLocalRoutes, String dnsIntegrationMethod, Boolean saveCookies) {
		try {
			/* Local per-user GUI specific configuration */
			Configuration config = Configuration.getDefault();

			if (trayMode != null)
				config.trayModeProperty().set(trayMode);

			if (darkMode != null)
				config.darkModeProperty().set(darkMode);

			if (logLevel != null) {
				config.logLevelProperty().set(logLevel);
				if (logLevel.length() == 0)
					org.apache.log4j.Logger.getRootLogger().setLevel(Main.getInstance().getDefaultLogLevel());
				else
					org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.toLevel(logLevel));
			}

			if (saveCookies != null) {
				config.saveCookiesProperty().set(saveCookies);
			}

			/* Update configuration stored globally in service */
			VPN vpn = context.getDBus().getVPN();
			boolean checkUpdates = false;
			if (phase != null) {
				String was = vpn.getValue(ConfigurationItem.PHASE.getKey());
				if (!Objects.equals(was, phase)) {
					vpn.setValue(ConfigurationItem.PHASE.getKey(), phase);
					checkUpdates = true;
				}
			}
			if (automaticUpdates != null) {
				boolean was = vpn.getBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey());
				if (!Objects.equals(was, automaticUpdates)) {
					vpn.setBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey(), automaticUpdates);
					checkUpdates = true;
				}
			}
			if (ignoreLocalRoutes != null) {
				vpn.setBooleanValue(ConfigurationItem.IGNORE_LOCAL_ROUTES.getKey(), ignoreLocalRoutes);
			}
			if (logLevel != null) {
				vpn.setValue(ConfigurationItem.LOG_LEVEL.getKey(), logLevel);
			}
			if (dnsIntegrationMethod != null) {
				vpn.setValue(ConfigurationItem.DNS_INTEGRATION_METHOD.getKey(), dnsIntegrationMethod);
			}

			if (automaticUpdates != null && checkUpdates) {
				new Thread() {
					public void run() {
						checkForUpdate();
					}
				}.start();
			} else if (darkMode != null) {
				UI.this.reapplyColors();
				setHtmlPage("options.html", true);
			}

			LOG.info("Saved options");
		} catch (Exception e) {
			showError("Failed to save options.", e);
		}
	}

	protected void setHtmlPage(String htmlPage) {
		setHtmlPage(htmlPage, false);
	}

	protected void setHtmlPage(String htmlPage, boolean force) {
		if (!Objects.equals(htmlPage, this.htmlPage) || force) {
			changeHtmlPage(htmlPage);
			pageBundle = null;
			try {

				CookieHandler cookieHandler = java.net.CookieHandler.getDefault();
				if (htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {

					/* Set the device UUID cookie for all web access */
					try {
						URI uri = new URI(htmlPage);
						Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
						headers.put("Set-Cookie", Arrays.asList(String.format("%s=%s",
								AbstractDBusClient.DEVICE_IDENTIFIER, context.getDBus().getVPN().getUUID())));
						cookieHandler.put(uri.resolve("/"), headers);
					} catch (Exception e) {
						throw new IllegalStateException("Failed to set cookie.", e);
					}
					webView.getEngine().setUserStyleSheetLocation(UI.class.getResource("remote.css").toExternalForm());
					if (htmlPage.contains("?"))
						htmlPage += "&_=" + Math.random();
					else
						htmlPage += "?_=" + Math.random();
					load(htmlPage);
				} else {

					/*
					 * The only way I can seem to get this to work is to use a Data URI. Local file
					 * URI does not work (resource also works, but we need a dynamic resource, and I
					 * couldn't get a custom URL handler to work either).
					 */
					File customLocalWebCSSFile = context.getCustomLocalWebCSSFile();
					if (customLocalWebCSSFile.exists()) {
						if (LOG.isDebugEnabled())
							LOG.debug(String.format("Setting user stylesheet at %s", customLocalWebCSSFile));
						byte[] localCss = IOUtils.toByteArray(customLocalWebCSSFile.toURI());
						String datauri = "data:text/css;base64," + Base64.getEncoder().encodeToString(localCss);
						webView.getEngine().setUserStyleSheetLocation(datauri);
					} else {
						if (LOG.isDebugEnabled())
							LOG.debug(String.format("No user stylesheet at %s", customLocalWebCSSFile));
					}

					setupPage();
					String loc = htmlPage;
					URL resource = UI.class.getResource(getBaseHtml());
					if (resource == null)
						throw new FileNotFoundException(String.format("No page named %s.", htmlPage));
					loc = resource.toExternalForm();
					String anchor = getAnchor();
					if (anchor != null)
						loc += "#" + anchor;

					URI uri = new URI(loc);
					Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
					headers.put("Set-Cookie", Arrays
							.asList(String.format("%s=%s", Client.LOCBCOOKIE, Client.localWebServerCookie.toString())));
					cookieHandler.put(uri.resolve("/"), headers);

					if (LOG.isDebugEnabled())
						LOG.debug(String.format("Loading location %s", loc));

					load(loc);
				}
			} catch (Exception e) {
				LOG.error("Failed to set page.", e);
			} finally {
				setAvailable();
			}
		} else {
			uiRefresh();
		}
	}

	protected void uiRefresh() {
		try {
			webView.getEngine().executeScript("uiRefresh();");
		} catch (Exception e) {
			log.debug(String.format("Page failed to execute uiRefresh()."), e);
		}
	}

	private void setupPage() {
		if (htmlPage == null || htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {
			pageBundle = resources;
		} else {
			int idx = htmlPage.lastIndexOf('?');
			if (idx != -1)
				htmlPage = htmlPage.substring(0, idx);
			idx = htmlPage.lastIndexOf('.');
			if (idx == -1) {
				htmlPage = htmlPage + ".html";
				idx = htmlPage.lastIndexOf('.');
			}
			String base = htmlPage.substring(0, idx);
			String res = Client.class.getName();
			idx = res.lastIndexOf('.');
			String resourceName = res.substring(0, idx) + "." + base;
			if (LOG.isDebugEnabled())
				LOG.debug(String.format("Loading bundle %s", resourceName));
			try {
				pageBundle = ResourceBundle.getBundle(resourceName);
			} catch (MissingResourceException mre) {
				// Page doesn't have resources
				LOG.debug(String.format("No resources for %s", resourceName));
				pageBundle = resources;
			}
		}
	}

	private VPNConnection getForegroundConnection() {
		if (htmlPage != null && isRemote(htmlPage)) {

		} else {
			String anchor = getAnchor();
			if (anchor != null) {
				try {
					return context.getDBus().getVPNConnection(Long.parseLong(anchor));
				} catch (Exception nfe) {
				}
			}
		}
		return getPriorityConnection();
	}

	private void processJavascript() {
		if (log.isDebugEnabled())
			log.debug("Processing page content");
		String baseHtmlPage = getBaseHtml();
		WebEngine engine = webView.getEngine();
		JSObject jsobj = (JSObject) engine.executeScript("window");
		jsobj.setMember("bridge", bridge);
		try {
			jsobj.setMember("vpn", Main.getInstance().getVPN());
		} catch (IllegalStateException ise) {
			/* No bus */
		}
		VPNConnection selectedConnection = getForegroundConnection();
		jsobj.setMember("connection", selectedConnection);
		jsobj.setMember("pageBundle", pageBundle);

		/* Special pages */
		if ("options.html".equals(baseHtmlPage)) {
			for (Map.Entry<String, Object> beanEn : beansForOptions().entrySet()) {
				log.debug(String.format(" Setting %s to %s", beanEn.getKey(), beanEn.getValue()));
				jsobj.setMember(beanEn.getKey(), beanEn.getValue());
			}
		} else if ("authorize.html".equals(baseHtmlPage)) {
			// TODO release this is page changes
			Semaphore authorizedLock = new Semaphore(1);
			try {
				authorizedLock.acquire();
			} catch (InterruptedException e1) {
				throw new IllegalStateException("Already acquired authorize lock.");
			}

			ServiceClientAuthenticator authenticator = new ServiceClientAuthenticator(context, engine, authorizedLock);
			jsobj.setMember("authenticator", authenticator);
			final ServiceClient serviceClient = new ServiceClient(Main.getInstance().getCookieStore(), authenticator);
			jsobj.setMember("serviceClient", serviceClient);
			jsobj.setMember("register", new Register(selectedConnection, serviceClient, this));
		}

		/* Override log. TODO: Not entirely sure this works entirely */
		if (!context.isChromeDebug()) {
			engine.executeScript("console.log = function(message)\n" + "{\n" + "    bridge.log(message);\n" + "};");
		}

		/* Signal to page we are ready */
		try {
			engine.executeScript("uiReady();");
		} catch (Exception e) {
			log.debug(String.format("Page %s failed to execute uiReady() functions.", baseHtmlPage), e);
		}

	}

	private String getAnchor() {
		if (htmlPage == null)
			return null;
		int idx = htmlPage.indexOf('#');
		if (idx != -1) {
			return htmlPage.substring(idx + 1);
		}
		return null;
	}

	private String getBaseHtml() {
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

	private void processDOM() {
		boolean busAvailable = context.getDBus().isBusAvailable();
		VPNConnection connection = busAvailable ? getForegroundConnection() : null;
		DOMProcessor processor = new DOMProcessor(busAvailable ? context.getDBus().getVPN() : null, connection,
				collections, lastErrorMessage, lastErrorCause, lastException, branding, pageBundle, resources,
				webView.getEngine().getDocument().getDocumentElement(),
				connection == null ? disconnectionReason : connection.getLastError());
		processor.process();
		collections.clear();
		lastException = null;
		lastErrorMessage = null;
		lastErrorCause = null;

		// TEMP DEBUG
//		TransformerFactory transformerFactory = TransformerFactory.newInstance();
//		Transformer transformer;
//		try {
//			transformer = transformerFactory.newTransformer();
//			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
//			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
//			DOMSource source = new DOMSource(webView.getEngine().getDocument());
//			StreamResult result = new StreamResult(System.out);
//			transformer.transform(source, result);
//		} catch (TransformerException e) {
//		}
	}

	private void connectToUri(String unprocessedUri) {
		context.getOpQueue().execute(() -> {
			try {
				LOG.info(String.format("Connected to URI %s", unprocessedUri));
				URI uriObj = Util.getUri(unprocessedUri);
				long connectionId = context.getDBus().getVPN().getConnectionIdForURI(uriObj.toASCIIString());
				if (connectionId == -1) {
					if (Main.getInstance().isCreateIfDoesntExist()) {
						/* No existing configuration */
						context.getDBus().getVPN().createConnection(uriObj.toASCIIString(), true, true,
								Mode.CLIENT.name());
					} else {
						showError(MessageFormat.format(bundle.getString("error.uriProvidedDoesntExist"), uriObj));
					}
				} else {
					reloadState(() -> {
						maybeRunLater(() -> {
							initUi();
						});
					});
				}

			} catch (Exception e) {
				showError("Failed to add connection.", e);
			}
		});
	}

	private void addConnection() {
		setHtmlPage("addLogonBoxVPN.html");
	}

	private void configure(String usernameHint, String configIniFile, VPNConnection config) {
		try {
			LOG.info(String.format("Configuration for %s on %s", usernameHint, config.getDisplayName()));
			config.setUsernameHint(usernameHint);
			String error = config.parse(configIniFile);
			if (StringUtils.isNotBlank(error))
				throw new IOException(error);

			config.save();
			selectPageForState(false, false);
			config.authorized();
		} catch (Exception e) {
			showError("Failed to configure connection.", e);
		}
	}

	private void setAsFavourite(VPNConnection sel) {
		sel.setAsFavourite();
	}

	private boolean confirmDelete(VPNConnection sel) {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.initModality(Modality.APPLICATION_MODAL);
		alert.initOwner(getStage());
		alert.setTitle(resources.getString("delete.confirm.title"));
		alert.setHeaderText(resources.getString("delete.confirm.header"));
		alert.setContentText(MessageFormat.format(resources.getString("delete.confirm.content"), sel.getUri(false)));
		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == ButtonType.OK) {
			try {
				doDelete(sel);
			} catch (Exception e) {
				log.error("Failed to delete connection.", e);
			}
			return true;
		} else {
			return false;
		}
	}

	private void doDelete(VPNConnection sel) throws RemoteException {
		setHtmlPage("busy.html");
		log.info(String.format("Deleting connection %s", sel));
		context.getOpQueue().execute(() -> sel.delete());
	}

	private void reloadState(Runnable then) {
		/*
		 * Care must be take that this doesn't run on the UI thread, as it may take a
		 * while.
		 */

//		mode = context.getDBus().isBusAvailable() && context.getDBus().getVPN().isUpdating() ? UIState.UPDATE : UIState.NORMAL;
		VPNConnection favouriteConnection = getFavouriteConnection();
		if (Client.allowBranding) {
			branding = getBranding(favouriteConnection);
		}
		var splashFile = getCustomSplashFile();
		if (branding == null) {
			log.info(String.format("Removing branding."));
			if (logoFile != null) {
				try {
					Files.delete(logoFile);
				}
				catch(IOException ioe) {
					log.warn("Failed to delete.", ioe);
				}
				logoFile = null;
			}
			try {
				Files.delete(splashFile);
			}
			catch(IOException ioe) {
				log.warn("Failed to delete.", ioe);
			}
			updateVMOptions(null);
		} else {
			if (log.isDebugEnabled())
				log.debug("Adding custom branding");
			String logo = branding.getLogo();

			/* Create branded splash */
			BufferedImage bim = null;
			Graphics2D graphics = null;
			if (branding.getResource() != null && StringUtils.isNotBlank(branding.getResource().getBackground())) {
				bim = new BufferedImage(SPLASH_WIDTH, SPLASH_HEIGHT, BufferedImage.TYPE_INT_ARGB);
				graphics = (Graphics2D) bim.getGraphics();
				graphics.setColor(java.awt.Color.decode(branding.getResource().getBackground()));
				graphics.fillRect(0, 0, SPLASH_WIDTH, SPLASH_HEIGHT);
			}

			/* Create logo file */
			if (StringUtils.isNotBlank(logo)) {
				var newLogoFile = getCustomLogoFile(favouriteConnection);
				try {
					if (!Files.exists(newLogoFile)) {
						log.info(String.format("Attempting to cache logo"));
						URL logoUrl = new URL(logo);
						URLConnection urlConnection = logoUrl.openConnection();
						urlConnection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
						urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(10));
						try (InputStream urlIn = urlConnection.getInputStream()) {
							try (OutputStream out = Files.newOutputStream(newLogoFile)) {
								urlIn.transferTo(out);
							}
						}
						log.info(String.format("Logo cached from %s to %s", logoUrl, newLogoFile.toUri()));
						newLogoFile.toFile().deleteOnExit();
					}
					logoFile = newLogoFile;
					branding.setLogo(logoFile.toUri().toString());

					/* Draw the logo on the custom splash */
					if (graphics != null) {
						log.info(String.format("Drawing logo on splash"));
						BufferedImage logoImage = ImageIO.read(logoFile.toFile());
						if (logoImage == null)
							throw new Exception(String.format("Failed to load image from %s", logoFile));
						graphics.drawImage(logoImage, (SPLASH_WIDTH - logoImage.getWidth()) / 2,
								(SPLASH_HEIGHT - logoImage.getHeight()) / 2, null);
					}

				} catch (Exception e) {
					log.error(String.format("Failed to cache logo"), e);
					branding.setLogo(null);
				}
			} else if (logoFile != null) {
				try {
					Files.delete(logoFile);
				}
				catch(IOException ioe) {
					log.warn("Failed to delete.", ioe);
				}
				logoFile = null;
			}

			/* Write the splash */
			if (graphics != null) {
				try {
					ImageIO.write(bim, "png", splashFile.toFile());
					log.info(String.format("Custom splash written to %s", splashFile));
				} catch (IOException e) {
					log.error(String.format("Failed to write custom splash"), e);
					try {
						Files.delete(splashFile);
					}
					catch(IOException ioe) {
						log.warn("Failed to delete.", ioe);
					}
				}
			}

			updateVMOptions(splashFile);
		}

		then.run();
	}
	
	private void updateVMOptions(Path splashFile) {
		var vmOptionsFile = getConfigDir().resolve("gui.vmoptions");
		List<String> lines;
		if(Files.exists(vmOptionsFile)) {
			try(BufferedReader r = Files.newBufferedReader(vmOptionsFile)) {
				lines = IOUtils.readLines(r);
			}
			catch(IOException ioe) {
				throw new IllegalStateException("Failed to read .vmoptions.", ioe);
			}
		}
		else {
			lines = new ArrayList<>();
		}
		for(Iterator<String> lineIt = lines.iterator(); lineIt.hasNext(); ) {
			String line = lineIt.next();
			if(line.startsWith("-splash:")) {
				lineIt.remove();
			}
		}
		if(splashFile != null && Files.exists(splashFile)) {
			lines.add(0, "-splash:" + splashFile.toAbsolutePath().toString());
		}
		if(lines.isEmpty()) {
			try {
				Files.delete(vmOptionsFile);
			} catch (IOException e) {
			}
		}
		else {
			try(BufferedWriter r = Files.newBufferedWriter(vmOptionsFile)) {
				IOUtils.writeLines(lines, System.getProperty("line.separator"), r);
			}
			catch(IOException ioe) {
				throw new IllegalStateException("Failed to read .vmoptions.", ioe);
			}
		}
	}

	private Path getCustomSplashFile() {
		return getConfigDir().resolve("lbvpnc-splash.png"); 
	}

	private Path getConfigDir() {
		var upath = System.getProperty("logonbox.vpn.configuration");
		return upath == null ? Paths.get(System.getProperty("user.home") + File.separator + ".logonbox-vpn-client") : Paths.get(upath);
	}

	private Path getCustomLogoFile(VPNConnection connection) {
		return getConfigDir().resolve("lpvpnclogo-" + (connection == null ? "default" : connection.getId()));
	}

	private void reapplyColors() {
		context.applyColors(branding, null);
	}

	private void reapplyLogo() {
		String defaultLogo = UI.class.getResource("logonbox-titlebar-logo.png").toExternalForm();
		if ((branding == null || StringUtils.isBlank(branding.getLogo())
				&& !defaultLogo.equals(titleBarImageView.getImage().getUrl()))) {
			titleBarImageView.setImage(new Image(defaultLogo, true));
		} else if (branding != null && !defaultLogo.equals(branding.getLogo())
				&& !StringUtils.isBlank(branding.getLogo())) {
			titleBarImageView.setImage(new Image(branding.getLogo(), true));
		}
	}

	private void editConnection(VPNConnection connection) {
		setHtmlPage("editConnection.html#" + connection.getId());
	}

	@FXML
	private void evtBack() {
		AbstractDBusClient busClient = context.getDBus();
		boolean busAvailable = busClient.isBusAvailable();
		if (busAvailable) {
			for (VPNConnection c : busClient.getVPNConnections()) {
				if (c.getStatus().equals(ConnectionStatus.Type.AUTHORIZING.name())) {
					c.disconnect(bundle.getString("cancelled"));
					selectPageForState(false, false);
					return;
				}
			}
			if (updateService.isUpdatesEnabled() && updateService.isNeedsUpdating()) {
				updateService.deferUpdate();
				return;
			}
			if (busClient.getVPN().getMissingPackages().length > 0) {
				return;
			}
		}
		WebHistory history = webView.getEngine().getHistory();
		if (history.getCurrentIndex() > 0)
			history.go(-1);
	}

	@FXML
	private void evtAddConnection() {
		addConnection();
	}

	@FXML
	private void evtClose() {
		context.maybeExit();
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
		context.getHostServices().showDocument(debuggerURL);
	}

	@FXML
	private void evtStartDebugger() {
		startDebugging((ex) -> {
			// failed to start
			log.error("Debug server failed to start: " + ex.getMessage());
			debuggerLink.textProperty().set("");
			startDebugger.setDisable(false);
//             updateDebugOff.run();
		}, () -> {
			log.info("Debug server started, debug URL: " + myJSBridge.getDebuggerURL());
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

	@FXML
	private void evtMinimize() {
		context.getStage().setIconified(true);
	}

	@FXML
	private void evtOptions() throws Exception {
		options();
	}

	private void giveUpWaitingForBridgeEstablish() {
		LOG.info("Given up waiting for bridge to start");
		resetAwaingBridgeEstablish();
		notify(null, resources.getString("givenUpWaitingForBridgeEstablish"), ToastType.ERROR);
		selectPageForState(false, false);
	}

	private void initUi() {
		log.info("Rebuilding URIs");
		try {
		} catch (Exception e) {
			log.error("Failed to load connections.", e);
		}
		context.applyColors(branding, getScene().getRoot());
		reapplyLogo();
	}

	private List<VPNConnection> getAllConnections() {
		return context.getDBus().isBusAvailable() ? context.getDBus().getVPNConnections() : Collections.emptyList();
	}

	private void resetAwaingBridgeEstablish() {
		if (awaitingBridgeEstablish != null) {
			awaitingBridgeEstablish.stop();
			awaitingBridgeEstablish = null;
		}
	}

	private void resetAwaingBridgeLoss() {
		if (awaitingBridgeLoss != null) {
			awaitingBridgeLoss.stop();
			awaitingBridgeLoss = null;
		}
	}

	private VPNConnection getAuthorizingConnection() {
		return getFirstConnectionForState(Type.AUTHORIZING);
	}

	private VPNConnection getConnectingConnection() {
		return getFirstConnectionForState(Type.CONNECTING);
	}

	private VPNConnection getFirstConnectionForState(Type... type) {
		if (context.getDBus().isBusAvailable()) {
			List<Type> tl = Arrays.asList(type);
			for (VPNConnection connection : context.getDBus().getVPNConnections().toArray(new VPNConnection[0])) {
				Type status = Type.valueOf(connection.getStatus());
				if (tl.contains(status)) {
					return connection;
				}
			}
		}
		return null;
	}

	private void selectPageForState(boolean connectIfDisconnected, boolean force) {
		try {
			AbstractDBusClient bridge = context.getDBus();
			boolean busAvailable = bridge.isBusAvailable();
			if (busAvailable && bridge.getVPN().getMissingPackages().length > 0) {
				log.warn(String.format("Missing software packages"));
				collections.put("packages", Arrays.asList(bridge.getVPN().getMissingPackages()));
				setHtmlPage("missingSoftware.html");
			} else {
				if (!busAvailable) {
					/* The bridge is not (yet?) connected */
					setHtmlPage("index.html");
				} else {

					/* Are ANY connections authorizing? */
					VPNConnection connection = getAuthorizingConnection();
					if (connection != null) {
						Mode mode = Mode.valueOf(connection.getMode());
						if (mode == Mode.SERVICE) {
							log.info("Authorizing service");
							setHtmlPage("authorize.html");
						} else {
							String authUrl = connection.getAuthorizeUri();
							log.info(String.format("Authorizing to %s", authUrl));
							setHtmlPage(authUrl);
						}

						/* Done */
						return;
					}
					if (getConnectingConnection() == null && busAvailable && updateService.isUpdatesEnabled()
							&& updateService.isNeedsUpdating()) {
						// An update is available
						log.warn(String.format("Update is available"));
						setHtmlPage("updateAvailable.html");
					} else {
						/* Otherwise connections page */
						if (context.getDBus().getVPNConnections().isEmpty()) {
							if (Main.getInstance().isNoAddWhenNoConnections()) {
								if (Main.getInstance().isConnect()
										|| StringUtils.isNotBlank(Main.getInstance().getUri()))
									setHtmlPage("busy.html");
								else
									setHtmlPage("connections.html");
							} else
								setHtmlPage("addLogonBoxVPN.html");
						} else {
							setHtmlPage("connections.html");
						}
					}
				}
			}
		} catch (Exception e) {
			showError("Failed to set page.", e);
		}

	}

	private void setUpdateProgress(int val, String text) {
		if (!Platform.isFxApplicationThread())
			Platform.runLater(() -> setUpdateProgress(val, text));
		else {
			if (htmlPage.equals("updating.html")) {
				Document document = webView.getEngine().getDocument();
				if (document == null) {
					log.warn(String.format("No document,  ignoring progress update '%s'.", text));
				} else {
					Element progressElement = document.getElementById("progress");
					progressElement.setAttribute("aria-valuenow", String.valueOf(val));
					progressElement.setAttribute("style", "width: " + String.valueOf(val) + "%");
					Element progressText = document.getElementById("progressText");
					progressText.setTextContent(text);
				}
			}
		}
	}

	private void showError(String error) {
		showError(error, (String) null);
	}

	private void showError(String error, String cause) {
		showError(error, cause, (String) null);
	}

	private void showError(String error, Throwable exception) {
		showError(error, null, exception);
	}

	private void showError(String error, String cause, String exception) {
		showError(error, cause, exception, "error.html");
	}

	private void showError(String error, String cause, String exception, String page) {
		maybeRunLater(() -> {
			LOG.error(error, exception);
			lastErrorCause = cause;
			lastErrorMessage = error;
			lastException = exception;
			setHtmlPage(page);
		});
	}

	private void showError(String error, String cause, Throwable exception) {
		maybeRunLater(() -> {
			LOG.error(error, exception);
			if (error == null && exception != null) {
				lastErrorMessage = exception.getMessage();
			} else {
				lastErrorMessage = error;
			}
			if ((cause == null || cause.equals("")) && exception != null && exception.getCause() != null) {
				lastErrorCause = exception.getCause().getMessage();
			} else {
				lastErrorCause = cause;
			}
			if (exception == null) {
				lastException = "";
			} else {
				StringWriter w = new StringWriter();
				exception.printStackTrace(new PrintWriter(w));
				lastException = w.toString();
			}
			setHtmlPage("error.html");
		});
	}

	private void update() {
		new Thread() {
			public void run() {
				try {
					updateService.update();
				} catch (IOException ioe) {
					showError("Failed to update.", ioe);
				}
			}
		}.start();
	}

	private void checkForUpdate() {
		try {
			updateService.checkForUpdate();
			maybeRunLater(() -> {
				if (updateService.isNeedsUpdating() && getAuthorizingConnection() == null) {
					log.info("Updated needed and no authorizing connections, checking page state.");
					selectPageForState(false, true);
				}
			});
		} catch (IOException ioe) {
			maybeRunLater(() -> {
				showError("Failed to check for updates.", ioe);
			});
		}
	}

	private void deferUpdate() {
		updateService.deferUpdate();
		selectPageForState(false, true);
	}

	public static void maybeRunLater(Runnable r) {
		if (Platform.isFxApplicationThread())
			r.run();
		else
			Platform.runLater(r);
	}

	public Branding getBranding(VPNConnection connection) {
		ObjectMapper mapper = new ObjectMapper();
		Branding branding = null;
		if (connection != null) {
			try {
				branding = getBrandingForConnection(mapper, connection);
			} catch (IOException ioe) {
				log.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
						connection.getPort()));
			}
		}
		if (branding == null) {
			for (VPNConnection conx : context.getDBus().getVPNConnections()) {
				try {
					branding = getBrandingForConnection(mapper, conx);
					break;
				} catch (IOException ioe) {
					log.info(String.format("Skipping %s:%d because it appears offline.", conx.getHostname(),
							conx.getPort()));
				}
			}
		}
		return branding;
	}

	protected Branding getBrandingForConnection(ObjectMapper mapper, VPNConnection connection)
			throws UnknownHostException, IOException, JsonProcessingException, JsonMappingException {
		synchronized (brandingCache) {
			BrandingCacheItem item = brandingCache.get(connection);
			if (item != null && item.isExpired()) {
				item = null;
			}
			if (item == null) {
				item = new BrandingCacheItem(branding);
				brandingCache.put(connection, item);
				String uri = connection.getUri(false) + "/api/brand/info";
				log.info(String.format("Retrieving branding from %s", uri));
				URL url = new URL(uri);
				URLConnection urlConnection = url.openConnection();
				urlConnection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(6));
				urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(6));
				try (InputStream in = urlConnection.getInputStream()) {
					Branding brandingObj = mapper.readValue(in, Branding.class);
					brandingObj.setLogo("https://" + connection.getHostname() + ":" + connection.getPort()
							+ connection.getPath() + "/api/brand/logo");
					item.branding = brandingObj;
				}
			}
			return item.branding;
		}
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

	private void setLoading(boolean loading) {
		if (this.loading.isVisible() != loading) {
			if (loading) {
				loadingRotation = new RotateTransition(Duration.millis(1000), loadingSpinner);
				loadingRotation.setByAngle(360);
				loadingRotation.setCycleCount(RotateTransition.INDEFINITE);
				loadingRotation.play();
			} else if (loadingRotation != null) {
				loadingRotation.stop();
			}
			this.loading.setVisible(loading);
		}
	}

	private void load(final String url) {
		setLoading(true);
		LOG.info(String.format("Loading page %s", htmlPage));
		// let it know that we are reloading the page, not chrome dev tools
		if (myJSBridge != null)
			myJSBridge.pageReloading();
		// load the web page
		webView.getEngine().load(url);
		log.info("Loaded " + url);
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
		assert myStateProvider != null;
		return myStateProvider.getState().getJsNumber("debugPort").intValue(51723);
	}

//	private void setPort(int port) {
//		assert myStateProvider != null;
//		myStateProvider.getState().put("debugPort", port);
//	}
}
