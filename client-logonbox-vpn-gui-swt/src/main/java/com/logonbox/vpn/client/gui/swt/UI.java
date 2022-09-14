package com.logonbox.vpn.client.gui.swt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.messages.DBusSignal;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonParser;
import com.hypersocket.json.AuthenticationRequiredResult;
import com.hypersocket.json.AuthenticationResult;
import com.hypersocket.json.input.InputField;
import com.logonbox.vpn.common.client.AbstractApp;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.AbstractDBusClient.BusLifecycleListener;
import com.logonbox.vpn.common.client.AuthenticationCancelledException;
import com.logonbox.vpn.common.client.ConfigurationItem;
import com.logonbox.vpn.common.client.ConfigurationItem.TrayMode;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.logonbox.vpn.common.client.ServiceClient;
import com.logonbox.vpn.common.client.ServiceClient.NameValuePair;
import com.logonbox.vpn.common.client.UpdateService;
import com.logonbox.vpn.common.client.Util;
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class UI implements BusLifecycleListener {

	static ResourceBundle BUNDLE = ResourceBundle.getBundle(UI.class.getName());

	public static final class ServiceClientAuthenticator implements ServiceClient.Authenticator {
		private final Browser engine;
		private final Semaphore authorizedLock;
		private final Client context;
		private Map<InputField, NameValuePair> results;
		private AuthenticationRequiredResult result;
		private boolean error;
		private Display display;
		private Main main;

		public ServiceClientAuthenticator(Main main, Display display, Client context, Browser engine,
				Semaphore authorizedLock) {
			this.main = main;
			this.display = display;
			this.engine = engine;
			this.context = context;
			this.authorizedLock = authorizedLock;
		}

		public void cancel() {
			LOG.info("Cancelling authorization.");
			error = true;
			authorizedLock.release();
		}

		public void submit(String obj) {
			var rootEl = JsonParser.parseString(obj);
			var rootObj = rootEl.getAsJsonObject();

			for (InputField field : result.getFormTemplate().getInputFields()) {
				results.put(field, new NameValuePair(field.getResourceKey(),
						rootObj.has(field.getResourceKey()) ? rootObj.get(field.getResourceKey()).getAsString() : ""));
			}
			authorizedLock.release();
		}

		@Override
		public String getUUID() {
			return context.getDBus().getVPN().getUUID();
		}

		@Override
		public HostnameVerifier getHostnameVerifier() {
			return main.getCertManager();
		}

		@Override
		public void error(JsonNode i18n, AuthenticationResult logonResult) {
			maybeRunLater(display, () -> {
				if (logonResult.isLastErrorIsResourceKey())
					engine.evaluate("authorizationError('"
							+ StringEscapeUtils.escapeEcmaScript(i18n.get(logonResult.getErrorMsg()).asText()) + "');",
							true);
				else
					engine.evaluate("authorizationError('"
							+ StringEscapeUtils.escapeEcmaScript(logonResult.getErrorMsg()) + "');", true);

			});
		}

		@Override
		public void collect(JsonNode i18n, AuthenticationRequiredResult result, Map<InputField, NameValuePair> results)
				throws IOException {
			this.results = results;
			this.result = result;

			maybeRunLater(display, () -> {
				// TODO
//				BoundObjects jsobj = (BoundObjects) engine.evaluate("window");
//				jsobj.setMember( "remoteBundle", i18n);
//				jsobj.setMember("result", result);
//				jsobj.setMember("results", results);
//				engine.evaluate("collect();");
			});
			try {
				LOG.info("Waiting for authorize semaphore to be released.");
				authorizedLock.acquire();
			} catch (InterruptedException e) {
				// TODO:
			}

			LOG.info("Left authorization.");
			if (error)
				throw new AuthenticationCancelledException();
		}

		@Override
		public void authorized() throws IOException {
			LOG.info("Authorized.");
			maybeRunLater(display, () -> {
				engine.evaluate("authorized();", true);
				authorizedLock.release();
			});
		}
	}

	public static final class Register implements Runnable {
		private final VPNConnection selectedConnection;
		private final ServiceClient serviceClient;
		private final UI ui;
		private final Display display;

		public Register(Display display, VPNConnection selectedConnection, ServiceClient serviceClient, UI ui) {
			this.selectedConnection = selectedConnection;
			this.serviceClient = serviceClient;
			this.ui = ui;
			this.display = display;
		}

		public void run() {
			new Thread() {
				public void run() {
					try {
						serviceClient.register(selectedConnection);
					} catch (AuthenticationCancelledException ae) {
						// Ignore, handled elsewhere
					} catch (IOException | URISyntaxException e) {
						maybeRunLater(display, () -> ui.showError("Failed to register.", e));
					}
				}
			}.start();
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

//	@SuppressWarnings("unchecked")
//	static <T> T memberOrDefault(JSObject obj, String member, Class<T> clazz, T def) {
//		try {
//			Object o = obj.getMember(member);
//			if ("undefined".equals(o)) {
//				/*
//				 * NOTE: Bit crap, means you can never actually have a value of "undefined", OK
//				 * for our case, but could cause future problems.
//				 */
//				throw new Exception(member + " not defined.");
//			}
//			if (o == null) {
//				return null;
//			}
//			return clazz.isAssignableFrom(o.getClass()) ? (T) o : def;
//		} catch (Exception | Error ex) {
//			return def;
//		}
//	}

	static int DROP_SHADOW_SIZE = 11;

	final static Logger LOG = LoggerFactory.getLogger(UI.class);

	private Map<VPNConnection, BrandingCacheItem> brandingCache = new HashMap<>();
	private List<VPNConnection> connecting = new ArrayList<>();
//	private String lastErrorMessage;
//	private String lastErrorCause;
//	private String lastException;
	private Path logoFile;
	private Runnable runOnNextLoad;
	private ServerBridge bridge;
	private Display display;
	private Map<Long, Map<Class<? extends DBusSignal>, DBusSigHandler<? extends DBusSignal>>> eventsMap = new HashMap<>();
	private UpdateService updateService;
	private Shell shell;
	private Main main;
	private Browser browser;
//	private ToolItem close;
//	private ToolItem minimize;
//	private ToolItem back;
	private FlatButton close;
	private FlatButton minimize;
	private FlatButton back;
	private Label titleBarImageView;
	private URL titleBarImageViewUrl;
	private Composite loading;
	private Label loadingSpinner;
	private Client client;
	private PageModel pageModel;

	public UI(Shell shell, Client client, Main main, Display display) {
		this.shell = shell;
		this.client = client;
		this.main = main;
		this.display = display;
		this.context = client;
		updateService = main.getUpdateService();
		pageModel = new PageModel(client, updateService, BUNDLE);
	}

	protected Client context;
	protected URL location;
	protected Composite scene;
//	private RotateTransition loadingRotation;

	private Composite stack;
	private StackLayout stackLayout;
	private Composite top;
	
	public Display getDisplay() {
		return display;
	}

	public final void configure() {

		pageModel.addPage("options.html", this::beansForOptions);
		pageModel.addPage("authorize.html", () -> {
			var jsobj = new HashMap<String, Object>();
			Semaphore authorizedLock = new Semaphore(1);
			try {
				authorizedLock.acquire();
			} catch (InterruptedException e1) {
				throw new IllegalStateException("Already acquired authorize lock.");
			}

			ServiceClientAuthenticator authenticator = new ServiceClientAuthenticator(main, display, context, browser,
					authorizedLock);
			jsobj.put("authenticator", authenticator);
			final ServiceClient serviceClient = new ServiceClient(main.getCookieStore(), authenticator, main.getCertManager());
			jsobj.put("serviceClient", serviceClient);
			jsobj.put("register", new Register(display, pageModel.getConnection(), serviceClient, this));
			return jsobj;
		});

		var shellLayout = new GridLayout(1, true);
		shell.setLayout(shellLayout);
		shellLayout.marginHeight = shellLayout.marginWidth = 0;

		var sceneLayout = new GridLayout(1, true);
		sceneLayout.verticalSpacing = 0;
		sceneLayout.marginHeight = sceneLayout.marginWidth = 0;
		scene = new Composite(shell, SWT.NONE);
		scene.setLayout(sceneLayout);
		var sceneLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
		scene.setLayoutData(sceneLayoutData);

		top = new Composite(scene, SWT.NONE);
		var topLayout = new GridLayout(4, false);
		topLayout.horizontalSpacing = 4;
		var topLayoutData = new GridData(GridData.FILL_HORIZONTAL);
		top.setLayoutData(topLayoutData);
		top.setLayout(topLayout);

		var bg = new GridData(32, 32);
		bg.horizontalAlignment = SWT.CENTER;
		bg.verticalAlignment = SWT.CENTER;

		if (SystemUtils.IS_OS_MAC_OSX) {
			if (!main.isNoClose() && !main.isWindowDecorations()) {
				close = new FlatButton(top, SWT.PUSH);
				close.setLayoutData(bg);
				close.setImage(FontIcon.image(display, FontAwesome.WINDOW_CLOSE, 32));
			}
			if (!main.isNoMinimize() && client.isMinimizeAllowed() && !main.isWindowDecorations()) {
				minimize = new FlatButton(top, SWT.PUSH);
				minimize.setImage(FontIcon.image(display, FontAwesome.WINDOW_MINIMIZE, 32));
				minimize.setLayoutData(bg);
			}

			titleBarImageView = new Label(top, SWT.NONE);

//			var gap = new FlatButton(top, SWT.NONE);
//			gap.setLayoutData(bg);

//			back = new FlatButton(top, SWT.PUSH);
//			back.setImage(FontIcon.image(display, FontAwesome.ARROW_CIRCLE_LEFT, 32));
//			back.setLayoutData(bg);
		} else {
			back = new FlatButton(top, SWT.FLAT | SWT.PUSH);
			back.setImage(FontIcon.image(display, FontAwesome.ARROW_CIRCLE_LEFT, 32));
			back.setLayoutData(bg);
//			var gap = new FlatButton(top, SWT.NONE);
//			gap.setLayoutData(bg);

			titleBarImageView = new Label(top, SWT.NONE);

			if (!main.isNoMinimize() && client.isMinimizeAllowed() && !main.isWindowDecorations()) {
				minimize = new FlatButton(top, SWT.PUSH);
				minimize.setImage(FontIcon.image(display, FontAwesome.WINDOW_MINIMIZE, 32));
				minimize.setLayoutData(bg);
			}
			if (!main.isNoClose() && !main.isWindowDecorations()) {
				close = new FlatButton(top, SWT.PUSH);
				close.setImage(FontIcon.image(display, FontAwesome.WINDOW_CLOSE, 32));
				close.setLayoutData(bg);
			}
		}

//		titleBarImageView.setText("LogonBox");
//		titleBarImageView.setFont(new Font(display, lbFont));
		var titleData = new GridData();
		titleData.grabExcessHorizontalSpace = titleData.grabExcessVerticalSpace = true;
		titleData.horizontalAlignment = SWT.CENTER;
		titleBarImageView.setLayoutData(titleData);
		titleBarImageView.setEnabled(false);
		titleBarImageView.setAlignment(SWT.CENTER);
		reapplyLogo();

		back.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
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
				if (!browser.back()) {
					selectPageForState(false, false);
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		if (close != null)
			close.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					context.maybeExit();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});

		if (minimize != null)
			minimize.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					getStage().setMinimized(true);
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});

		stack = new Composite(scene, SWT.NONE);
		stackLayout = new StackLayout();
		var stackLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
		stack.setLayoutData(stackLayoutData);
		stack.setLayout(stackLayout);

		loading = new Composite(stack, SWT.NONE);
		loadingSpinner = new Label(loading, SWT.NONE);
		loadingSpinner.setText("Loading... (TODO)");

		browser = new Browser(stack, SWT.NONE);
		pageModel.setBrowser(browser);
		var browserLayoutData = new GridData(GridData.FILL_BOTH);
		browser.setLayoutData(browserLayoutData);
		stackLayout.topControl = browser;

		titleBarImageView.pack();
		top.pack();
		browser.pack();
		stack.pack();
		scene.pack();

		bridge = new ServerBridge(this);
		pageModel.setBridge(bridge);

		setAvailable();

		/* Configure engine */
		configureWebEngine();

		/* Watch for update check state changing */
		main.getUpdateService().addListener(() -> {
			maybeRunLater(display, () -> {
				if (getAuthorizingConnection() == null)
					selectPageForState(false, false);
			});
		});

		/* Initial page */
		try {
			context.getDBus().addBusLifecycleListener(this);
			context.getDBus().getVPN().ping();
			selectPageForState(false, false);
		} catch (Exception e) {
			setHtmlPage("index.html");
		}
	}

	public Composite getTop() {
		return top;
	}

	public void applyColors(Colors colors) {
		// TODO dispose of colors
		var bg = new Color(display, colors.getBgColor());
		var fg = new Color(display, colors.getFgColor());

		top.setBackground(bg);
		top.setForeground(fg);
	}

	public Shell getStage() {
		return shell;
	}

	public Composite getScene() {
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
				display.asyncExec(() -> showError("Failed to disconnect.", e));
			}
		});
	}

	public void connect(VPNConnection n) {
		try {
			if (n == null) {
				addConnection();
			} else {
				LOG.info(String.format("Connect to %s", n.getAddress()));
				if (n != null) {
					Type status = Type.valueOf(n.getStatus());
					LOG.info(String.format("  current status is %s", status));
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
			beans.put("trayMode", TrayMode.AUTO.name());
		} else {
			try {
				beans.put("phases", updateService.getPhases());
			} catch (Exception e) {
				LOG.warn("Could not get phases.", e);
			}

			/* Configuration stored globally in service */
			beans.put("phase", vpn.getValue(ConfigurationItem.PHASE.getKey()));
			beans.put("dnsIntegrationMethod", vpn.getValue(ConfigurationItem.DNS_INTEGRATION_METHOD.getKey()));
			beans.put("trayMode", vpn.getValue(ConfigurationItem.TRAY_MODE.getKey()));

			/* Store locally in preference */
			beans.put("automaticUpdates", vpn.getBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey()));
			beans.put("ignoreLocalRoutes", vpn.getBooleanValue(ConfigurationItem.IGNORE_LOCAL_ROUTES.getKey()));
		}

		/* Option collections */
		beans.put("trayModes", Arrays.asList(TrayMode.values()).stream().map(TrayMode::name)
				.collect(Collectors.toUnmodifiableList()).toArray(new String[0]));
		beans.put("darkModes", new String[] { Configuration.DARK_MODE_AUTO, Configuration.DARK_MODE_ALWAYS,
				Configuration.DARK_MODE_NEVER });
		beans.put("logLevels",
				new String[] { "", Level.OFF.getName(), Level.SEVERE.getName(), Level.WARNING.getName(),
						Level.INFO.getName(), Level.FINE.getName(), Level.FINER.getName(), Level.FINEST.getName(),
						Level.ALL.getName() });
		beans.put("dnsIntegrationMethods", Arrays.asList(DNSIntegrationMethod.valuesForOs()).stream()
				.map(DNSIntegrationMethod::name).collect(Collectors.toUnmodifiableList()).toArray(new String[0]));

		/* Per-user GUI specific */
		Configuration config = Configuration.getDefault();
		beans.put("darkMode", config.darkModeProperty());
		beans.put("logLevel", config.logLevelProperty() == null ? "" : config.logLevelProperty());
		beans.put("saveCookies", config.saveCookiesProperty());

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
							maybeRunLater(display, () -> {
								try {
									VPNConnection addedConnection = context.getDBus().getVPNConnection(sig.getId());
									try {
										listenConnectionEvents(connection, addedConnection);
									} catch (DBusException e) {
										throw new IllegalStateException("Failed to listen for new connections events.");
									}

									context.getOpQueue().execute(() -> {
										reloadState(() -> {
											maybeRunLater(display, () -> {
												reapplyColors();
												reapplyLogo();
												if (addedConnection.isAuthorized())
													joinNetwork(addedConnection);
												else
													authorize(addedConnection);
											});
										});
									});
								} finally {
									LOG.info("Connection added");
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
							maybeRunLater(display, () -> {
								try {
									context.getOpQueue().execute(() -> {
										reloadState(() -> {
											maybeRunLater(display, () -> {
												reapplyColors();
												reapplyLogo();
												selectPageForState(false, false);
											});
										});
									});
								} finally {
									LOG.info("Connection deleted");
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
							maybeRunLater(display, () -> {
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
									maybeRunLater(display, () -> {
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
				String unprocessedUri = main.getUri();
				if (StringUtils.isNotBlank(unprocessedUri)) {
					connectToUri(unprocessedUri);
				} else {
					initUi();
					selectPageForState(main.isConnect(), false);
				}
			}
		};

		/*
		 * If we were waiting for this, it's part of the update process. We don't want
		 * the connection continuing
		 */
		reloadState(() -> maybeRunLater(display, runnable));
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
						pageModel.setDisconnectionReason(null);
						Mode authMode = Connection.Mode.valueOf(sig.getMode());
						if (authMode.equals(Connection.Mode.CLIENT) || authMode.equals(Connection.Mode.SERVICE)) {
							maybeRunLater(display, () -> {
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
						pageModel.setDisconnectionReason(null);
						maybeRunLater(display, () -> {
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
						maybeRunLater(display, () -> {
							connecting.remove(connection);
							if (main.isExitOnConnection()) {
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
						maybeRunLater(display, () -> {
							String s = sig.getReason();
							LOG.info(String.format("Failed to connect. %s", s));
							connecting.remove(connection);
							showError("Failed to connect.", s, sig.getTrace());
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
						pageModel.setDisconnectionReason(sig.getReason());
						maybeRunLater(display, () -> {
							LOG.info("Temporarily offline " + sig.getId());
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
						maybeRunLater(display, () -> {
							LOG.info("Disconnected " + sig.getId());
							selectPageForState(false, false);

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
						maybeRunLater(display, () -> {
							LOG.info("Disconnecting " + bus /* + " (delete " + deleteOnDisconnect + ")" */);
							selectPageForState(false, false);
						});
					}
				}));
	}

	@Override
	public void busGone() {
		LOG.info("Bridge lost");
		maybeRunLater(display, () -> {
			connecting.clear();
			initUi();
			selectPageForState(false, false);
		});
	}

	protected void joinNetwork(VPNConnection connection) {
		context.getOpQueue().execute(() -> {
			connection.connect();
		});
	}

	protected void addConnection(Boolean stayConnected, Boolean connectAtStartup, String unprocessedUri, Mode mode)
			throws URISyntaxException {
		URI uriObj = Util.getUri(unprocessedUri);
		context.getOpQueue().execute(() -> {
			try {
				context.getDBus().getVPN().createConnection(uriObj.toASCIIString(), connectAtStartup, stayConnected,
						mode.name());
			} catch (Exception e) {
				showError("Failed to add connection.", e);
			}
		});
	}

	protected void importConnection(Path file) throws IOException {
		try (var r = Files.newBufferedReader(file)) {
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
		LOG.info("Page is now {}", htmlPage);
		pageModel.setHtmlPage(htmlPage);
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
		browser.setJavascriptEnabled(true);
		browser.addProgressListener(new ProgressListener() {

			@Override
			public void changed(ProgressEvent event) {
			}

			@Override
			public void completed(ProgressEvent event) {

				try {
					if (!pageModel.isProcessed()) {
						LOG.info("Not processed, doing now.");
						URL url;
						if(Http.isUrl(browser.getText())) {
							url = Http.url(browser.getText());
						}
						else {
							url = Http.url(pageModel.getUrl(), browser.getText());
						}
						// if (isRemote(htmlPage)) { // TODO ... only expose SOME variables to remote
						// pages, and then only ones from the known server
						processDOM(url, Jsoup.parse(browser.getText(), Http.parentURL(browser.getUrl())), false);
					}

					/* Signal to page we are ready */
					LOG.info("Signalling browser ready.");
					browser.execute("uiReady();");
				} catch (Exception e) {
					LOG.error("Page {} failed to execute uiReady() functions.", pageModel.getHtmlPage(),
							e);
				}

				pageModel.reset();
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
		browser.addLocationListener(new LocationListener() {
			@Override
			public void changing(LocationEvent event) {
			}

			@Override
			public void changed(LocationEvent event) {
				String newLoc = event.location;
				if (newLoc != null) {
					if (isExclude(newLoc)) {
						LOG.info(String.format("Ignoring page change to %s.", newLoc));
						return;
					} else if (Http.isRemote(newLoc)) {
						LOG.info(String.format("This is a remote page (%s), not changing current html page", newLoc));
						changeHtmlPage(null);
					} else {
						LOG.info(String.format("This is a local page (%s), changing current html page", newLoc));
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
//						if (idx != -1) {
//							base = base.substring(0, idx);
//						}
						if (!base.equals("") && !base.equals(pageModel.getHtmlPage())) {
							changeHtmlPage(base);
							LOG.debug(String.format("Page changed by user to %s (from browser view)",
									pageModel.getHtmlPage()));
						}
					}
					setupPage();
				}
			}
		});
//		engine.getLoadWorker().exceptionProperty().addListener((o, old, value) -> {
//			if (value == null) {
//				/* Error cleared */
//				return;
//			}
//
//			/*
//			 * If we are authorizing and get an error from the browser, then it's likely the
//			 * target server is offline and can't do the authorization.
//			 * 
//			 * So cancel the authorize and show the error and allow retry.
//			 */
//			VPNConnection priorityConnection = getPriorityConnection();
//			try {
//				if (priorityConnection != null && ConnectionStatus.Type
//						.valueOf(priorityConnection.getStatus()) == ConnectionStatus.Type.AUTHORIZING) {
//					String reason = value != null ? value.getMessage() : null;
//					LOG.info(String.format("Got error while authorizing. Disconnecting now using '%s' as the reason",
//							reason));
//					context.getOpQueue().execute(() -> {
//						try {
//							priorityConnection.disconnect(reason);
//						} catch (Exception e) {
//							Platform.runLater(() -> showError("Failed to disconnect.", e));
//						}
//					});
//
//					/*
//					 * Await the disconnected event that comes back from the service and process it
//					 * then
//					 */
//					return;
//				}
//			} catch (Exception e) {
//				LOG.error("Failed to adjust authorizing state.", e);
//			}
//			if (!checkForInvalidatedSession(value)) {
//				showError("Error.", value);
//			}
//		});
	}

	protected boolean isExclude(String newLoc) {
		return (newLoc.startsWith("about:"));
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

	public void setAvailable() {
		boolean isNoBack = "missingSoftware.html".equals(pageModel.getHtmlPage())
				|| "connections.html".equals(pageModel.getHtmlPage()) || !context.getDBus().isBusAvailable()
				|| ("addLogonBoxVPN.html".equals(pageModel.getHtmlPage())
						&& context.getDBus().getVPNConnections().isEmpty());
		back.setVisible(!isNoBack);
		top.redraw();

	}

	protected void saveOptions(String trayMode, String darkMode, String phase, Boolean automaticUpdates,
			String logLevel, Boolean ignoreLocalRoutes, String dnsIntegrationMethod, Boolean saveCookies) {
		try {
			/* Local per-user GUI specific configuration */
			Configuration config = Configuration.getDefault();

			if (darkMode != null)
				config.darkModeProperty(darkMode);

			if (logLevel != null) {
				config.logLevelProperty(logLevel);
				if (logLevel.length() == 0)
					main.setLogLevel(main.getDefaultLogLevel());
				else
					main.setLogLevel(AbstractApp.parseLogLevel(logLevel));
			}

			if (saveCookies != null) {
				config.saveCookiesProperty(saveCookies);
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
			if (trayMode != null) {
				vpn.setValue(ConfigurationItem.TRAY_MODE.getKey(), trayMode);
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
		if (!Objects.equals(htmlPage, pageModel.getHtmlPage()) || force) {
			changeHtmlPage(htmlPage);
			pageModel.setPageBundle(null);
			try {

				CookieHandler cookieHandler = CookieManager.getDefault();
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
					pageModel.setUserStyleSheetLocation(UI.class.getResource("remote.css").toExternalForm());
					if (htmlPage.contains("?"))
						htmlPage += "&_=" + Math.random();
					else
						htmlPage += "?_=" + Math.random();
					load(htmlPage, new HashMap<>());
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
						pageModel.setUserStyleSheetLocation(datauri);
					} else {
						if (LOG.isDebugEnabled())
							LOG.debug(String.format("No user stylesheet at %s", customLocalWebCSSFile));
						pageModel.setUserStyleSheetLocation(null);
					}

					setupPage();
					String loc = htmlPage;
					URL resource = UI.class.getResource(pageModel.getBaseHtml());
					if (resource == null)
						throw new FileNotFoundException(String.format("No page named %s.", htmlPage));
					loc = resource.toExternalForm();
					String anchor = pageModel.getAnchor();
					if (anchor != null)
						loc += "#" + anchor;

					URI uri = new URI(loc);
					Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
					headers.put("Set-Cookie", Arrays
							.asList(String.format("%s=%s", Client.LOCBCOOKIE, Client.localWebServerCookie.toString())));
					cookieHandler.put(uri.resolve("/"), headers);

					if (LOG.isInfoEnabled())
						LOG.info(String.format("Loading location %s", loc));

					load(loc, headers);
				}
			} catch (Exception e) {
				LOG.error("Failed to set page {}.", htmlPage, e);
			} finally {
				setAvailable();
			}
		} else {
			uiRefresh();
		}
	}

	protected void uiRefresh() {
		try {
			browser.evaluate("uiRefresh();", true);
		} catch (Exception e) {
			LOG.debug(String.format("Page failed to execute uiRefresh()."), e);
		}
	}

	private void setupPage() {
		var htmlPage = pageModel.getHtmlPage();
		if (htmlPage == null || htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {
			pageModel.setPageBundle(BUNDLE);
		} else {
			int idx = htmlPage.lastIndexOf('?');
			if (idx != -1)
				htmlPage = htmlPage.substring(0, idx);
			idx = htmlPage.lastIndexOf('.');
			if (idx == -1) {
				htmlPage = htmlPage + ".html";
				idx = htmlPage.lastIndexOf('.');
			}
			pageModel.setHtmlPage(htmlPage);
			String base = htmlPage.substring(0, idx);
			String res = Client.class.getName();
			idx = res.lastIndexOf('.');
			String resourceName = res.substring(0, idx) + "." + base;
			if (LOG.isDebugEnabled())
				LOG.debug(String.format("Loading bundle %s", resourceName));
			try {
				pageModel.setPageBundle(ResourceBundle.getBundle(resourceName));
			} catch (MissingResourceException mre) {
				// Page doesn't have resources
				LOG.debug(String.format("No resources for %s", resourceName));
				pageModel.setPageBundle(BUNDLE);
			}
		}
	}

	VPNConnection getForegroundConnection() {
		if (pageModel.getHtmlPage() != null && pageModel.isRemote()) {

		} else {
			String anchor = pageModel.getAnchor();
			if (anchor != null) {
				try {
					return context.getDBus().getVPNConnection(Long.parseLong(anchor));
				} catch (Exception nfe) {
				}
			}
		}
		return getPriorityConnection();
	}

	private void processDOM(URL url, Document doc, boolean workOnDocument) {
		if (workOnDocument)
			LOG.info("Processing DOM, output to DOM");
		else
			LOG.info("Processing DOM, output as Javascript");
		pageModel.setDocument(doc);
		pageModel.setUrl(url);
		pageModel.setConnection(getForegroundConnection());
		pageModel.process(workOnDocument);
	}

	private void connectToUri(String unprocessedUri) {
		context.getOpQueue().execute(() -> {
			try {
				LOG.info(String.format("Connected to URI %s", unprocessedUri));
				URI uriObj = Util.getUri(unprocessedUri);
				long connectionId = context.getDBus().getVPN().getConnectionIdForURI(uriObj.toASCIIString());
				if (connectionId == -1) {
					if (main.isCreateIfDoesntExist()) {
						/* No existing configuration */
						context.getDBus().getVPN().createConnection(uriObj.toASCIIString(), true, true,
								Mode.CLIENT.name());
					} else {
						showError(MessageFormat.format(bundle.getString("error.uriProvidedDoesntExist"), uriObj));
					}
				} else {
					reloadState(() -> {
						maybeRunLater(display, () -> {
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

	void configure(String usernameHint, String configIniFile, VPNConnection config) {
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

	void setAsFavourite(VPNConnection sel) {
		sel.setAsFavourite();
	}

	boolean confirmDelete(VPNConnection sel) {
		MessageBox alert = new MessageBox(shell, SWT.CANCEL | SWT.OK | SWT.ICON_WARNING);
		alert.setText(BUNDLE.getString("delete.confirm.title"));
//		alert.setHeaderText(resources.getString("delete.confirm.header"));
		alert.setMessage(MessageFormat.format(BUNDLE.getString("delete.confirm.content"), sel.getUri(false)));
		var ok = alert.open() == SWT.OK;
		if (ok) {

			try {
				doDelete(sel);
			} catch (Exception e) {
				LOG.error("Failed to delete connection.", e);
			}
			return true;
		} else {
			return false;
		}
	}

	private void doDelete(VPNConnection sel) throws RemoteException {
		setHtmlPage("busy.html");
		LOG.info(String.format("Deleting connection %s", sel));
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
			pageModel.setBranding(getBranding(favouriteConnection));
		}
		var splashFile = getCustomSplashFile();
		if (pageModel.getBranding() == null) {
			LOG.info(String.format("Removing branding."));
			if (logoFile != null) {
				try {
					Files.delete(logoFile);
				} catch (IOException ioe) {
					LOG.warn("Failed to delete.", ioe);
				}
				logoFile = null;
			}
			if (Files.exists(splashFile)) {
				try {
					Files.delete(splashFile);
				} catch (IOException ioe) {
					LOG.warn("Failed to delete.", ioe);
				}
			}
			updateVMOptions(null);
		} else {
			if (LOG.isDebugEnabled())
				LOG.debug("Adding custom branding");
			String logo = pageModel.getBranding().getLogo();

			/* Create branded splash */
//			BufferedImage bim = null;
//			Graphics2D graphics = null;
//			if (branding.getResource() != null && StringUtils.isNotBlank(branding.getResource().getBackground())) {
//				bim = new BufferedImage(SPLASH_WIDTH, SPLASH_HEIGHT, BufferedImage.TYPE_INT_ARGB);
//				graphics = (Graphics2D) bim.getGraphics();
//				graphics.setColor(java.awt.Color.decode(branding.getResource().getBackground()));
//				graphics.fillRect(0, 0, SPLASH_WIDTH, SPLASH_HEIGHT);
//			}

			/* Create logo file */
			if (StringUtils.isNotBlank(logo)) {
				var newLogoFile = getCustomLogoFile(favouriteConnection);
				try {
					if (!Files.exists(newLogoFile)) {
						LOG.info(String.format("Attempting to cache logo"));
						URL logoUrl = new URL(logo);
						URLConnection urlConnection = logoUrl.openConnection();
						urlConnection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
						urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(10));
						try (InputStream urlIn = urlConnection.getInputStream()) {
							try (OutputStream out = Files.newOutputStream(newLogoFile)) {
								urlIn.transferTo(out);
							}
						}
						LOG.info(String.format("Logo cached from %s to %s", logoUrl, newLogoFile.toUri()));
						newLogoFile.toFile().deleteOnExit();
					}
					logoFile = newLogoFile;
					pageModel.getBranding().setLogo(logoFile.toUri().toString());

					/* Draw the logo on the custom splash */
//					if (graphics != null) {
//						LOG.info(String.format("Drawing logo on splash"));
//						BufferedImage logoImage = ImageIO.read(logoFile.toFile());
//						if (logoImage == null)
//							throw new Exception(String.format("Failed to load image from %s", logoFile));
//						graphics.drawImage(logoImage, (SPLASH_WIDTH - logoImage.getWidth()) / 2,
//								(SPLASH_HEIGHT - logoImage.getHeight()) / 2, null);
//					}

				} catch (Exception e) {
					LOG.error(String.format("Failed to cache logo"), e);
					pageModel.getBranding().setLogo(null);
				}
			} else if (logoFile != null) {
				try {
					Files.delete(logoFile);
				} catch (IOException ioe) {
					LOG.warn("Failed to delete.", ioe);
				}
				logoFile = null;
			}

			/* Write the splash */
//			if (graphics != null) {
//				try {
//					ImageIO.write(bim, "png", splashFile.toFile());
//					LOG.info(String.format("Custom splash written to %s", splashFile));
//				} catch (IOException e) {
//					LOG.error(String.format("Failed to write custom splash"), e);
//					try {
//						Files.delete(splashFile);
//					} catch (IOException ioe) {
//						LOG.warn("Failed to delete.", ioe);
//					}
//				}
//			}

			updateVMOptions(splashFile);
		}

		then.run();
	}

	private void updateVMOptions(Path splashFile) {
		var vmOptionsFile = getConfigDir().resolve("gui.vmoptions");
		List<String> lines;
		if (Files.exists(vmOptionsFile)) {
			try (BufferedReader r = Files.newBufferedReader(vmOptionsFile)) {
				lines = IOUtils.readLines(r);
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to read .vmoptions.", ioe);
			}
		} else {
			lines = new ArrayList<>();
		}
		for (Iterator<String> lineIt = lines.iterator(); lineIt.hasNext();) {
			String line = lineIt.next();
			if (line.startsWith("-splash:")) {
				lineIt.remove();
			}
		}
		if (splashFile != null && Files.exists(splashFile)) {
			lines.add(0, "-splash:" + splashFile.toAbsolutePath().toString());
		}
		if (lines.isEmpty()) {
			try {
				Files.delete(vmOptionsFile);
			} catch (IOException e) {
			}
		} else {
			try (BufferedWriter r = Files.newBufferedWriter(vmOptionsFile)) {
				IOUtils.writeLines(lines, System.getProperty("line.separator"), r);
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to read .vmoptions.", ioe);
			}
		}
	}

	private Path getCustomSplashFile() {
		return getConfigDir().resolve("lbvpnc-splash.png");
	}

	private Path getConfigDir() {
		var upath = System.getProperty("logonbox.vpn.configuration");
		return upath == null ? Paths.get(System.getProperty("user.home") + File.separator + ".logonbox-vpn-client")
				: Paths.get(upath);
	}

	private Path getCustomLogoFile(VPNConnection connection) {
		return getConfigDir().resolve("lpvpnclogo-" + (connection == null ? "default" : connection.getId()));
	}

	private void reapplyColors() {
		context.applyColors(pageModel.getBranding(), null);
	}

	private void reapplyLogo() {

		// TODO dispose images
		var defaultLogo = UI.class.getResource("logonbox-titlebar-logo.png");
		var branding = pageModel.getBranding();
		if ((branding == null
				|| StringUtils.isBlank(branding.getLogo()) && !defaultLogo.equals(titleBarImageViewUrl))) {
			titleBarImageView.setImage(Images.scaleToHeight(loadImage(titleBarImageViewUrl = defaultLogo), 32));
			titleBarImageView.pack();
		} else if (branding != null && !defaultLogo.toExternalForm().equals(branding.getLogo())
				&& !StringUtils.isBlank(branding.getLogo())) {
			try {
				titleBarImageView.setImage(
						(Images.scaleToHeight(loadImage(titleBarImageViewUrl = new URL(branding.getLogo())), 32)));
				titleBarImageView.pack();
			} catch (MalformedURLException e) {
				throw new IllegalStateException("Failed to apply logo.", e);
			}
		}
	}

	private Image loadImage(URL url) {
		LOG.info("Loading image {}", url);
		try (var in = url.openStream()) {
			return new Image(display, in);
		} catch (Exception ioe) {
			throw new IllegalStateException("Failed to load image.", ioe);
		}
	}

	void editConnection(VPNConnection connection) {
		setHtmlPage("editConnection.html#" + connection.getId());
	}

	void initUi() {
		LOG.info("Rebuilding URIs");
		try {
		} catch (Exception e) {
			LOG.error("Failed to load connections.", e);
		}
		context.applyColors(pageModel.getBranding(), getScene());
		reapplyLogo();
	}

	List<VPNConnection> getAllConnections() {
		return context.getDBus().isBusAvailable() ? context.getDBus().getVPNConnections() : Collections.emptyList();
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

	void selectPageForState(boolean connectIfDisconnected, boolean force) {
		try {
			AbstractDBusClient bridge = context.getDBus();
			boolean busAvailable = bridge.isBusAvailable();
			if (busAvailable && bridge.getVPN().getMissingPackages().length > 0) {
				LOG.warn(String.format("Missing software packages"));
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
							LOG.info("Authorizing service");
							setHtmlPage("authorize.html");
						} else {
							String authUrl = connection.getAuthorizeUri();
							LOG.info(String.format("Authorizing to %s", authUrl));
							setHtmlPage(authUrl);
						}

						/* Done */
						return;
					}
					if (getConnectingConnection() == null && busAvailable && updateService.isUpdatesEnabled()
							&& updateService.isNeedsUpdating()) {
						// An update is available
						LOG.warn(String.format("Update is available"));
						setHtmlPage("updateAvailable.html");
					} else {
						/* Otherwise connections page */
						if (context.getDBus().getVPNConnections().isEmpty()) {
							if (main.isNoAddWhenNoConnections()) {
								if (main.isConnect() || StringUtils.isNotBlank(main.getUri()))
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

	void showError(String error) {
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
		pageModel.error(error, cause, exception);
		maybeRunLater(display, () -> {
			setHtmlPage(page);
		});
	}

	private void showError(String error, String cause, Throwable exception) {
		String lastErrorMessage, lastErrorCause, lastException;
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
		showError(lastErrorMessage, lastErrorCause, lastException, "error.html");
	}

	void update() {
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

	void checkForUpdate() {
		try {
			updateService.checkForUpdate();
			maybeRunLater(display, () -> {
				if (updateService.isNeedsUpdating() && getAuthorizingConnection() == null) {
					LOG.info("Updated needed and no authorizing connections, checking page state.");
					selectPageForState(false, true);
				}
			});
		} catch (IOException ioe) {
			maybeRunLater(display, () -> {
				showError("Failed to check for updates.", ioe);
			});
		}
	}

	void deferUpdate() {
		updateService.deferUpdate();
		selectPageForState(false, true);
	}

	public static void maybeRunLater(Display display, Runnable r) {
		if (Display.getCurrent() != null)
			r.run();
		else
			display.asyncExec(r);
	}

	public Branding getBranding(VPNConnection connection) {
		ObjectMapper mapper = new ObjectMapper();
		Branding branding = null;
		if (connection != null) {
			try {
				branding = getBrandingForConnection(mapper, connection);
			} catch (IOException ioe) {
				LOG.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
						connection.getPort()));
			}
		}
		if (branding == null) {
			for (VPNConnection conx : context.getDBus().getVPNConnections()) {
				try {
					branding = getBrandingForConnection(mapper, conx);
					break;
				} catch (IOException ioe) {
					LOG.info(String.format("Skipping %s:%d because it appears offline.", conx.getHostname(),
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
				item = new BrandingCacheItem(pageModel.getBranding());
				brandingCache.put(connection, item);
				String uri = connection.getUri(false) + "/api/brand/info";
				LOG.info(String.format("Retrieving branding from %s", uri));
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

	private void setLoading(boolean loading) {
//		if (this.loading.isVisible() != loading) {
//			if (loading) {
//				loadingRotation = new RotateTransition(Duration.millis(1000), loadingSpinner);
//				loadingRotation.setByAngle(360);
//				loadingRotation.setCycleCount(RotateTransition.INDEFINITE);
//				loadingRotation.play();
//			} else if (loadingRotation != null) {
//				loadingRotation.stop();
//			}
//			stackLayout.topControl = loading ? this.loading : browser;
//			this.loading.setVisible(loading);
//			scene.pack();
//		}
	}

	private void load(final String url, Map<String, List<String>> cookies) {
		setLoading(true);
		LOG.info("Loading page {} using {}", pageModel.getHtmlPage(), url);
		// let it know that we are reloading the page, not chrome dev tools
		// load the web page

		// XXXXXXXXXXXXXX
		// TODO
		//
		// XXX 1. Load ourselves, parse into Document (w3c preferably)
		// 1a. Remote resources (http://)
		// 2a. Add cookies

		// 5. Inject User-Agent?

		Document doc;
		try {
			var urlObj = new URL(url);
			try (var in = urlObj.openStream()) {
				doc = Jsoup.parse(in, "UTF-8", Http.parentURL(url));
			} catch (IOException /* | SAXException | ParserConfigurationException */ ioe) {
				throw new IllegalStateException("Failed to load page.", ioe);
			}
			processDOM(urlObj, doc, true);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Failed to load page.", e);
		}

		browser.setText(doc.toString(), true);

		LOG.info("Loaded " + url);
	}

	public static FontData loadFont(int sz, String resourceName, String fontName, Display swtDisplay) {
		FontData fontdata = null;
		var fontFile = UI.class.getResource(resourceName);
		File realFontFile = null;
		if (fontFile != null && (!fontFile.getProtocol().equals("file") || !new File(fontFile.getPath()).exists())) {
			try {
				realFontFile = File.createTempFile("swt", ".ttf");
				realFontFile.deleteOnExit();
				try (var fos = new FileOutputStream(realFontFile)) {
					try (var in = fontFile.openStream()) {
						in.transferTo(fos);
					}
				}
				fontFile = realFontFile.toURI().toURL();
			} catch (IOException ioe) {
				fontFile = null;
			}
		}
		boolean isLoaded = false;
		if (fontFile != null) {
			try {
				isLoaded = swtDisplay.loadFont(new File(fontFile.toURI()).getAbsolutePath());
			} catch (URISyntaxException e) {
			}
		}
		try {
			if (isLoaded) {
				var fd = Display.getCurrent().getFontList(null, true);
				for (var element : fd) {
					if (element.getName().equals(fontName)) {
						fontdata = element;
						break;
					}
				}
				if (fontdata != null) {
					fontdata.setHeight(sz);
				}
			}
			return fontdata;
		} finally {
			if (realFontFile != null)
				realFontFile.delete();
		}
	}

	public Shell getShell() {
		return shell;
	}
}
