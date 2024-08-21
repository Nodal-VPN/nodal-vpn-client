package com.logonbox.vpn.client.gui.jfx;

import static com.logonbox.vpn.client.common.Utils.isNotBlank;
import static javafx.application.Platform.runLater;

import com.logonbox.vpn.client.common.AuthMethod;
import com.logonbox.vpn.client.common.BrandingManager;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.ConnectionUtil;
import com.logonbox.vpn.client.common.DarkMode;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.ServiceClient;
import com.logonbox.vpn.client.common.TrayMode;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.drivers.lib.util.OsUtil;
import com.logonbox.vpn.drivers.lib.util.Util;
import com.sshtools.jaul.Phase;
import com.sshtools.jaul.UpdateService;

import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
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
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Bundle
@Resource(siblings = true)
@Reflectable
public final class UI<CONX extends IVpnConnection> extends AnchorPane {


	static UUID localWebServerCookie = UUID.randomUUID();

	static final String LOCBCOOKIE = "LOCBCKIE";

	private static final String DEFAULT_LOCALHOST_ADDR = "http://localhost:59999/";
	private final ThreadLocal<Boolean> checkForUpdate = new ThreadLocal<>();

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
				if (Utils.isBlank(server))
					throw new IllegalArgumentException(bundle.getString("error.invalidUri"));
				Mode mode = Mode.valueOf(memberOrDefault(o, "mode", String.class, Mode.CLIENT.name()));
				UI.this.addConnection(stayConnected, connectAtStartup, server, mode);
			}
			else {
				UI.this.importConnection(Paths.get(configurationFile));
			}
		}

		public void setAsFavourite(long id) {
			UI.this.setAsFavourite(vpnManager.getVpnOrFail().getConnection(id));
		}

		public void confirmDelete(long id) {
			UI.this.confirmDelete(vpnManager.getVpnOrFail().getConnection(id));
		}
		
		public String browse() {
			var fileChooser = new FileChooser();
			var cfgDir = JavaFXUiConfiguration.getDefault().configurationFileProperty();
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
			UI.this.selectPageForState(false, true, false);
		}

		public void details(long id) {
			setHtmlPage("details.html#" + id);
		}

		public void edit(long id) {
			UI.this.editConnection(vpnManager.getVpnOrFail().getConnection(id));
		}

        public void go(String page) {
            setHtmlPage(page);
        }

		public void connectTo(long id) {
			UI.this.connect(vpnManager.getVpnOrFail().getConnection(id));
		}

		public void disconnectFrom(long id) {
			UI.this.disconnect(vpnManager.getVpnOrFail().getConnection(id));
		}

		public void editConnection(JSObject o) {
			Boolean connectAtStartup = (Boolean) o.getMember("connectAtStartup");
			Boolean stayConnected = (Boolean) o.getMember("stayConnected");
			String server = (String) o.getMember("serverUrl");
			String name = (String) o.getMember("name");
			UI.this.editConnection(connectAtStartup, stayConnected, name, server, getForegroundConnection());
		}

		public IVpnConnection getConnection() {
			return UI.this.getForegroundConnection();
		}

		public String getOS() {
			return OsUtil.getOS();
		}

		public String getDeviceName() {
			return Util.getDeviceName();
		}

		public String getUserPublicKey() {
			IVpnConnection connection = getConnection();
			return connection == null ? null : connection.getUserPublicKey();
		}

		public void log(String message) {
			LOG.debug("WEB: " + message);
		}

		public void reload() {
			UI.this.initUi();
		}
        
        public void copyToClipboard(String text) {
            maybeRunLater(() -> {
                var clipboard = Clipboard.getSystemClipboard();
                var content = new ClipboardContent();
                content.putString(text);
                clipboard.setContent(content);
            });
        }

		public void saveOptions(JSObject o) {
			String trayMode = memberOrDefault(o, "trayMode", String.class, null);
			String darkMode = memberOrDefault(o, "darkMode", String.class, null);
			String logLevel = memberOrDefault(o, "logLevel", String.class, null);
			String dnsIntegrationMethod = memberOrDefault(o, "dnsIntegrationMethod", String.class, null);
			String phase = memberOrDefault(o, "phase", String.class, null);
			Boolean automaticUpdates = memberOrDefault(o, "automaticUpdates", Boolean.class, null);
			Boolean ignoreLocalRoutes = memberOrDefault(o, "ignoreLocalRoutes", Boolean.class, null);
			Integer mtu = memberOrDefault(o, "mtu", Integer.class, null);
			Boolean singleActiveConnection = memberOrDefault(o, "singleActiveConnection", Boolean.class, null);
            Boolean ipForwarding = memberOrDefault(o, "ipForwarding", Boolean.class, null);
			UI.this.saveOptions(trayMode, darkMode, phase, automaticUpdates, logLevel, ignoreLocalRoutes, ipForwarding,
					dnsIntegrationMethod, mtu, singleActiveConnection);
		}

		public void showError(String error) {
			UI.this.showError(error);
		}

		public void unjoin(String reason) {
			var sel = getConnection();
			UI.this.disconnect(sel, reason);
		}

		public void unjoinAll(String reason) {
			UI.this.disconnectAll(reason);
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
            try {
                uiContext.openURL(url);
            }
            catch(Exception e) {
                LOG.error("Failed to open browser.", e);
            }
		}
        
		public String getLastHandshake() {
			IVpnConnection connection = getConnection();
			return connection == null ? null
					: DateFormat.getDateTimeInstance().format(new Date(connection.getLastHandshake()));
		}

		public String getUsage() {
			IVpnConnection connection = getConnection();
			return connection == null ? null
					: MessageFormat.format(bundle.getString("usageDetail"), Util.toHumanSize(connection.getRx()),
							Util.toHumanSize(connection.getTx()));
		}

		public IVpnConnection[] getConnections() {
			var l = new ArrayList<>(getAllConnections());
			var f = getFavouriteConnection(l);
			if(f != null) {
				l.remove(f);
				l.add(0, f);
			}
			return l.toArray(new IVpnConnection[0]);
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

	final static Logger LOG = LoggerFactory.getLogger(UI.class);

	private Timeline awaitingBridgeEstablish;
	private Timeline awaitingBridgeLoss;
	private Map<String, Collection<String>> collections = new HashMap<>();
	private List<IVpnConnection> connecting = new ArrayList<>();
	private String htmlPage;
	private String lastErrorMessage;
	private String lastErrorCause;
	private String lastException;
	private ResourceBundle pageBundle;
	private ServerBridge bridge;
	private String disconnectionReason;

	@FXML
	@Reflectable
	private Label messageIcon;
	@FXML
	@Reflectable
	private Label messageText;
	@FXML
	@Reflectable
	private Hyperlink options;
	@FXML
	@Reflectable
	private VBox root;
	@FXML
	@Reflectable
	private WebView webView;
	@FXML
	@Reflectable
	private AnchorPane loading;
	@FXML
	@Reflectable
	private FontIcon loadingSpinner;
	@FXML
	@Reflectable
	private Parent debugBar;
	@FXML
	@Reflectable
	private Hyperlink debuggerLink;
	@FXML
	@Reflectable
	private Button startDebugger;
	@FXML
	@Reflectable
	private Button stopDebugger;

	private final UpdateService updateService;
    private final JfxAppContext<CONX> appContext;
    private final VpnManager<CONX> vpnManager;
    private final BrandingManager<CONX> brandingManager;

	
	public UI(UIContext<CONX> uiContext) {
		this.uiContext = uiContext;
		this.appContext = uiContext.getAppContext();
		this.vpnManager = appContext.getVpnManager();
        this.updateService = appContext.getUpdateService();
        
        // https://stackoverflow.com/questions/34036207/javafx-could-not-load-font-face-font-because-of-com-sun-javafx-css-stylemanager
        // BUG - This workaround for when FontAwesome (web) cannot be found when the Jar it is in, 
        // is located in a path with spaces! Fun ..
        try {
            var fontStr = UI.class.getResource("fontawesome/css/fonts/FontAwesome.ttf");
    		var fixedFontStr = fontStr.toExternalForm().replace("%20", " ");
    		Font.loadFont(fixedFontStr, 12);
        }
        catch(Exception e) {
        	e.printStackTrace();
        }
		
		location = getClass().getResource("UI.fxml");
		brandingManager = new BrandingManager<>(vpnManager, uiContext.getImageHandler());
		vpnManager.getVpn().ifPresent(vpn -> {
		    brandingManager.apply(getFavouriteConnection());
		});
		
		var loader = new FXMLLoader(location);
		loader.setController(this);
		loader.setRoot(this);
		loader.setResources(bundle);
		try {
			loader.load();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
		Font.loadFont(UI.class.getResource("ARLRDBD.TTF").toExternalForm(), 12);
		
		bridge = new ServerBridge();

		// create JSBridge Instance
		uiContext.debugger().ifPresent(d -> d.start(webView));

		setAvailable();

		/* Configure engine */
		configureWebEngine();

		/* Watch for update check state changing */
		updateService.setOnAvailableVersion(ver -> {
		    if(!Boolean.TRUE.equals(checkForUpdate.get()) && getAuthorizingConnection() == null)
		    maybeRunLater(() -> selectPageForState(false, false, false));
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

		stopDebugger.disableProperty().bind(Bindings.not(startDebugger.disableProperty()));

		/* Initial page */
//		setHtmlPage("index.html");
		
		
		/* Connection Added */
		vpnManager.onConnectionAdded(conx -> {
		    uiContext.getAppContext().getScheduler().execute(() -> {
                reloadState(() -> {
                    maybeRunLater(() -> {
                        uiContext.reapplyBranding();
                        if(conx.isAuthorized())
                            joinNetwork(conx);
                        else
                            authorize(conx);
                    });
                });
            });
		});
        
        /* Connection Removed */
		vpnManager.onConnectionRemoved(id -> {
		    maybeRunLater(() -> {
                try {
                    uiContext.getAppContext().getScheduler().execute(() -> {
                        reloadState(() -> {
                            maybeRunLater(() -> {
                                uiContext.reapplyBranding();
                                selectPageForState(false, false, false);
                            });
                        });
                    });
                } finally {
                    LOG.info("Connection deleted");
                }
            });
		});
        
        /* Connection Updated */
		vpnManager.onConnectionUpdated(conx -> {
            maybeRunLater(() -> {
                selectPageForState(false, false, false);
            });
        });
        
        /* Global configuration updated */
		vpnManager.onGlobalConfigChanged((cfg, val) -> {
            if (cfg.equals(ConfigurationItem.FAVOURITE)) {
                reloadState(() -> {
                    maybeRunLater(() -> {
                        initUi();
                        refresh();
                    });
                });
            }
        });
        
        /* Authorize */
		vpnManager.onAuthorize((conx, uri, mode, authMethods) -> {
            disconnectionReason = null;
            if(vpnManager.isBackendAvailable()) {
                
            }
            if(mode != Mode.MODERN) { 
                if (mode.equals(Connection.Mode.CLIENT) || mode.equals(Connection.Mode.SERVICE)) {
                    maybeRunLater(() -> {
                        uiContext.open();
                        // setHtmlPage(connection.getUri(false) + sig.getUri());
                        selectPageForState(false, false, false);
                    });
                } else {
                    LOG.info("This client doest not handle the authorization mode {}", mode);
                }
            }
            else {
                maybeRunLater(() -> {
                    uiContext.open();
                    selectPageForState(false, false, false);
                });
            }
        });
        
        /* Connecting */
		vpnManager.onConnecting(conx -> {
            disconnectionReason = null;
            maybeRunLater(() -> {
                selectPageForState(false, false, false);
            });
        });
        
        /* Connected */
		vpnManager.onConnected(conx -> {
            maybeRunLater(() -> {
                connecting.remove(conx);
                if (uiContext.getAppContext().isExitOnConnection()) {
                    uiContext.exitApp();
                } else
                    selectPageForState(false, true, false);
            });
        });
        
        /* Disconnecting */
		vpnManager.onDisconnecting((conx, reason) -> {
            maybeRunLater(() -> {
                LOG.info("Disconnecting {}", conx.getDisplayName());
                selectPageForState(false, false, false);
            });
        });

        /* Disconnected */
		vpnManager.onDisconnected((conx, reason) -> {
            maybeRunLater(() -> {
                LOG.info("Disconnected " + conx.getId());
                    selectPageForState(false, false, false);

            });
        });
        
        /* Temporarily offline */
		vpnManager.onTemporarilyOffline((conx, reason) -> {
            maybeRunLater(() -> {
                LOG.info("Temporarily offline {}", conx.getId());
                selectPageForState(false, false, false);
            });
        });
        
        /* Blocked offline */
        vpnManager.onBlocked((conx) -> {
            maybeRunLater(() -> {
                LOG.info("Blocked {}", conx.getId());
                selectPageForState(false, false, false);
            });
        });
        
        /* Failure */
		vpnManager.onFailure((conx, reason, cause, trace) -> {
            maybeRunLater(() -> {
                LOG.info("Failed to connect. {}", reason);
                connecting.remove(conx);
                showError("Failed to connect.", reason, trace);
                uiRefresh();
            });
        });
        
		vpnManager.onVpnGone(this::vpnGone);
		vpnManager.onVpnAvailable(this::vpnAvailable);
		
		try {
		    vpnManager.checkVpnManagerAvailable();
			selectPageForState(false, false, uiContext.getAppContext().isOptions());
		} catch (Exception e) {
			setHtmlPage("index.html");
		}
	}

	protected UIContext<CONX> uiContext;
	protected URL location;
	private RotateTransition loadingRotation;

    private Map<String, Object> beansForOptions;

	public final void cleanUp() {
	}

	public Stage getStage() {
		return (Stage) getScene().getWindow();
	}

	public void disconnect(IVpnConnection sel) {
		disconnect(sel, null);
	}

	public void disconnect(IVpnConnection sel, String reason) {
		if (sel == null)
			throw new IllegalArgumentException("No connection given.");
		if (reason == null)
			LOG.info("Requesting disconnect, no reason given");
		else
			LOG.info(String.format("Requesting disconnect, because '{}'", reason));

		uiContext.getAppContext().getScheduler().execute(() -> {
			try {
				sel.disconnect(Utils.defaultIfBlank(reason, ""));
			} catch (Exception e) {
				Platform.runLater(() -> showError("Failed to disconnect.", e));
			}
		});
	}

	public void disconnectAll(String reason) {
		if (reason == null)
			LOG.info("Requesting disconnect of all, no reason given");
		else
			LOG.info("Requesting disconnect, because '{}'", reason);

		uiContext.getAppContext().getScheduler().execute(() -> {
			try {
			    vpnManager.getVpnOrFail().disconnectAll();
			} catch (Exception e) {
				Platform.runLater(() -> showError("Failed to disconnect.", e));
			}
		});
	}

	public void connect(IVpnConnection n) {
		try {
			if (n == null) {
				addConnection();
			} else {
				LOG.info("Connect to {}", n.getAddress());
				if (n != null) {
					Type status = Type.valueOf(n.getStatus());
					LOG.info("  current status is {}", status);
					if (status == Type.CONNECTED || status == Type.CONNECTING || status == Type.DISCONNECTING)
						selectPageForState(false, false, false);
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
		var beans = new HashMap<String, Object>();
		var vpn = vpnManager.isBackendAvailable() ? vpnManager.getVpnOrFail() : null;
		if (vpn == null) {
			beans.put("phases", new String[0]);
			beans.put("phase", "");
			beans.put("automaticUpdates", true);
			beans.put("ignoreLocalRoutes", true);
			beans.put("dnsIntegrationMethod", "AUTO");
			beans.put("singleActiveConnection", true);
			beans.put("mtu", 0);
	        beans.put("dnsIntegrationMethods", new String[] { "AUTO" });
		} else {
			try {
				beans.put("phases", Arrays.asList(updateService.getPhases()).stream().map(Phase::name).toList().toArray(new String[0]));
			} catch (Exception e) {
				LOG.warn("Could not get phases.", e);
			}

			/* Configuration stored globally in service */
			beans.put("phase", vpn.getValue(ConfigurationItem.PHASE.getKey()));
			beans.put("dnsIntegrationMethod", vpn.getValue(ConfigurationItem.DNS_INTEGRATION_METHOD.getKey()));

			/* Store locally in preference */
			beans.put("singleActiveConnection", vpn.getBooleanValue(ConfigurationItem.SINGLE_ACTIVE_CONNECTION.getKey()));
			beans.put("mtu", vpn.getIntValue(ConfigurationItem.MTU.getKey()));
			beans.put("automaticUpdates", vpn.getBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey()));
			beans.put("ignoreLocalRoutes", vpn.getBooleanValue(ConfigurationItem.IGNORE_LOCAL_ROUTES.getKey()));
            beans.put("ipForwarding", vpn.getBooleanValue(ConfigurationItem.IP_FORWARDING.getKey()));
			
			var l = new ArrayList<String>();
			l.add("AUTO");
			l.addAll(Arrays.asList(vpn.getAvailableDNSMethods()));
	        beans.put("dnsIntegrationMethods", l.toArray(new String[0]));
		}

		/* Option collections */
		beans.put("trayModes", TrayMode.values());
		beans.put("darkModes", DarkMode.values());
		beans.put("logLevels", Arrays.asList(Level.values()).stream().map(Level::toString).collect(Collectors.toList()).toArray(new String[0]));

		/* Per-user GUI specific */
		var config = JavaFXUiConfiguration.getDefault();
		beans.put("trayMode", config.trayModeProperty().get());
		beans.put("darkMode", config.darkModeProperty().get());
		beans.put("logLevel", config.logLevelProperty().get() == null ? "" : config.logLevelProperty().get());

		return beans;
	}

	public void options() {
		setHtmlPage("options.html");
	}

	public void refresh() {
		selectPageForState(false, true, false);
	}

	public void reload() {
//		webView.getEngine().reload();
		/* TODO: hrm, why cant we just refresh the page? */
		selectPageForState(false, true, false);
	}

	

	protected void joinNetwork(IVpnConnection connection) {
	    uiContext.getAppContext().getScheduler().execute(() -> {
			connection.connect();
		});
	}

	protected void addConnection(Boolean stayConnected, Boolean connectAtStartup, String unprocessedUri, Mode mode)
			throws URISyntaxException {
		URI uriObj = ConnectionUtil.getUri(unprocessedUri);
		uiContext.getAppContext().getScheduler().execute(() -> {
			try {
			    vpnManager.getVpnOrFail().createConnection(uriObj.toASCIIString(), connectAtStartup, stayConnected,
						mode.name());
			} catch (Exception e) {
				showError("Failed to add connection.", e);
			}
		});
	}

	protected void importConnection(Path file)
			throws IOException {
		try(var r = Files.newBufferedReader(file)) {
			String content = Utils.toString(r);
			uiContext.getAppContext().getScheduler().execute(() -> {
				try {
				    vpnManager.getVpnOrFail().importConfiguration(content);
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

	protected void authorize(IVpnConnection n) {
	    uiContext.getAppContext().getScheduler().execute(() -> {
			try {
				n.authorize();
			} catch (Exception e) {
				showError("Failed to join VPN.", e);
			}
		});
	}

	protected void configureWebEngine() {
		WebEngine engine = webView.getEngine();
		webView.setContextMenuEnabled(uiContext.isContextMenuAllowed());
		String ua = engine.getUserAgent();
		LOG.info("User Agent: " + ua);
		/* TODO changing user agent may be trickier, iirc the legacy server examines for something */
		engine.setUserAgent(ua + " " + "JADVPNClient/"+ appContext.getVersion());
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
					LOG.info(String.format("This is a remote page (%s), not changing current html page", newLoc));
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
						LOG.debug(String.format("Page changed by user to %s (from browser view)", htmlPage));
					}
				}
				setupPage();
			}
		});
		engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
			if (newState == State.SUCCEEDED) {
				uiContext.debugger().ifPresent(d -> d.loadReady());
				
				/* Wait for a little while if pageBundle is null */

                if(checkPayload()) {
    				processDOM();
    				processJavascript();
    				setLoading(false);
                }
			}
		});
		engine.getLoadWorker().exceptionProperty().addListener((o, old, value) -> {
			if (value == null) {
				/* Error cleared */
				return;
			}
			value.printStackTrace();

			/*
			 * If we are authorizing and get an error from the browser, then it's likely the
			 * target server is offline and can't do the authorization.
			 * 
			 * So cancel the authorize and show the error and allow retry.
			 */
			IVpnConnection priorityConnection = getPriorityConnection();
			try {
				if (priorityConnection != null && ConnectionStatus.Type
						.valueOf(priorityConnection.getStatus()) == ConnectionStatus.Type.AUTHORIZING) {
					String reason = value != null ? value.getMessage() : null;
					LOG.info(String.format("Got error while authorizing. Disconnecting now using '%s' as the reason",
							reason));
					uiContext.getAppContext().getScheduler().execute(() -> {
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

    private boolean checkPayload() {
        var uri = webView.getEngine().getLocation();
        if(uri != null) {
            var idx = uri.indexOf("/vpn-payload/");
            try {
                if(idx == -1) {
                    idx = uri.indexOf("/vpn-error/");
                    if(idx == -1) {
                        return true;
                    }
                    else {
                        showError(new String(Base64.getDecoder().decode(uri.substring(idx + 11)), "UTF-8"));
                        return false;
                    }
                }
                else {
                    var data = uri.substring(idx + 13);
                    idx = data.indexOf('/');
                    configure(data.substring(0, idx), new String(Base64.getDecoder().decode(data.substring(idx + 1)), "UTF-8"), UI.this.getForegroundConnection());
                    return false;
                }
            } catch (UnsupportedEncodingException e) {
                throw new UncheckedIOException(e);
            }
        }
        return true;
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
				selectPageForState(false, false, false);
				return true;
			}
		}
		return false;
	}

	protected void editConnection(Boolean connectAtStartup, Boolean stayConnected, String name, String server,
			IVpnConnection connection) {
		try {
			URI uriObj = ConnectionUtil.getUri(server);

			connection.setName(name);
			connection.setHostname(uriObj.getHost());
			connection
					.setPort(uriObj.getPort() < 1 ? (uriObj.getScheme().equals("https") ? 443 : 80) : uriObj.getPort());
			connection.setConnectAtStartup(connectAtStartup);
			connection.setPath(uriObj.getPath());
			connection.setStayConnected(stayConnected);
			if (!connection.isTransient())
				connection.save();

		} catch (Exception e) {
			showError("Failed to save connection.", e);
		}
	}

	protected CONX getFavouriteConnection() {
		return getFavouriteConnection(getAllConnections());
	}

	protected CONX getFavouriteConnection(Collection<CONX> list) {
		var fav = list.isEmpty() ? 0l : vpnManager.getVpnOrFail().getLongValue(ConfigurationItem.FAVOURITE.getKey());
		for (var connection : list) {
			if (list.size() == 1 || connection.getId() == fav)
				return connection;
		}
		return list.isEmpty() ? null : list.iterator().next();
	}

	protected IVpnConnection getPriorityConnection() {
		var alls = getAllConnections();
		if (alls.isEmpty())
			return null;

		/*
		 * New UI, we use the connection that needs the most attention. If / when it
		 * becomes the default, then this will not be necessary.
		 */
		for (var connection : alls) {
			var type = Type.valueOf(connection.getStatus());
			if (type == Type.AUTHORIZING) {
				return connection;
			}
		}

		for (var connection : alls) {
			var type = Type.valueOf(connection.getStatus());
			if (type == Type.CONNECTING || type == Type.DISCONNECTING) {
				return connection;
			}
		}

		for (var connection : alls) {
			var type = Type.valueOf(connection.getStatus());
			if (type == Type.CONNECTED) {
				return connection;
			}
		}

		return alls.get(0);
	}

	public void setAvailable() {
		boolean isNoBack = "missingSoftware.html".equals(htmlPage) || "connections.html".equals(htmlPage)
				|| !vpnManager.isBackendAvailable()
				|| ("addLogonBoxVPN.html".equals(htmlPage) && vpnManager.getVpnOrFail().getConnections().length == 0);
		var nav = uiContext.navigator();
		if(nav != null)
		    nav.setBackVisible(!isNoBack);
		debugBar.setVisible(uiContext.debugger().isPresent());
	}

	protected void saveOptions(String trayMode, String darkMode, String phase, Boolean automaticUpdates,
			String logLevel, Boolean ipForwarding, Boolean ignoreLocalRoutes, String dnsIntegrationMethod, Integer mtu, Boolean singleActiveConnection) {
		try {
			/* Local per-user GUI specific configuration */
			JavaFXUiConfiguration config = JavaFXUiConfiguration.getDefault();

			if (trayMode != null) {
				var trayModeVal = TrayMode.valueOf(trayMode);
				if(config.trayModeProperty().get().equals(TrayMode.OFF) && trayModeVal != TrayMode.OFF) {
				    uiContext.startTray();
				}
                config.trayModeProperty().set(trayModeVal);
			}

			if (darkMode != null) {
                var darkModeVal = DarkMode.valueOf(darkMode);
				config.darkModeProperty().set(darkModeVal);
			}

			if (logLevel != null) {
				config.logLevelProperty().set(logLevel);
                LoggingConfig logging = uiContext.getAppContext().getLogging();
				if (logLevel.length() == 0)
					logging.setLevel(logging.getDefaultLevel()); 
				else {
					try { 
					    logging.setLevel(Level.valueOf(logLevel));
						}
					catch(IllegalArgumentException iae) {
                        logging.setLevel(logging.getDefaultLevel());
					}
				}
			}

			/* Update configuration stored globally in service */
			var vpn = vpnManager.getVpnOrFail();
			var checkUpdates = false;
			if (phase != null) {
				var was = vpn.getValue(ConfigurationItem.PHASE.getKey());
				if (!Objects.equals(was, phase)) {
					vpn.setValue(ConfigurationItem.PHASE.getKey(), phase);
					checkUpdates = true;
				}
			}
			if (automaticUpdates != null) {
				var was = vpn.getBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey());
				if (!Objects.equals(was, automaticUpdates)) {
					vpn.setBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey(), automaticUpdates);
					checkUpdates = true;
				}
			}
			if (ignoreLocalRoutes != null) {
				vpn.setBooleanValue(ConfigurationItem.IGNORE_LOCAL_ROUTES.getKey(), ignoreLocalRoutes);
			}
            if (ipForwarding != null) {
                vpn.setBooleanValue(ConfigurationItem.IP_FORWARDING.getKey(), ipForwarding);
            }
			if (singleActiveConnection != null) {
				vpn.setBooleanValue(ConfigurationItem.SINGLE_ACTIVE_CONNECTION.getKey(), singleActiveConnection);
			}
			if (mtu != null) {
				vpn.setIntValue(ConfigurationItem.MTU.getKey(), mtu);
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
                uiContext.reapplyBranding();
				setHtmlPage("options.html", true);
			}

			LOG.info("Saved options");
		} catch (Exception e) {
			showError("Failed to save options.", e);
		}
	}

	public void setHtmlPage(String htmlPage) {
		setHtmlPage(htmlPage, false);
	}

	protected void setHtmlPage(String htmlPage, boolean force) {
		if (!Objects.equals(htmlPage, this.htmlPage) || force) {
			changeHtmlPage(htmlPage);
			pageBundle = null;
			try {

				CookieHandler cookieHandler = CookieManager.getDefault();
				if (htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {
					
					var isServer = vpnManager.getVpnOrFail().isMatchesAnyServerURI(htmlPage);
					
					if(isServer) {
						/* Set the device UUID cookie for all web access */
						try {
							URI uri = new URI(htmlPage);
							Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
							headers.put("Set-Cookie", Arrays.asList(String.format("%s=%s",
									VpnManager.DEVICE_IDENTIFIER, vpnManager.getVpnOrFail().getUUID())));
							cookieHandler.put(uri.resolve("/"), headers);
						} catch (Exception e) {
							throw new IllegalStateException("Failed to set cookie.", e);
						}
					}

					if(isServer) {
						webView.getEngine().setUserStyleSheetLocation(UI.class.getResource("remote.css").toExternalForm());
						if (htmlPage.contains("?"))
							htmlPage += "&_=" + Math.random();
						else
							htmlPage += "?_=" + Math.random();
					}
					else {
						webView.getEngine().setUserStyleSheetLocation(null);
					}
					load(htmlPage);
						
				} else {

					/*
					 * The only way I can seem to get this to work is to use a Data URI. Local file
					 * URI does not work (resource also works, but we need a dynamic resource, and I
					 * couldn't get a custom URL handler to work either).
					 */
					var customLocalWebCSSFile = uiContext.styling().getCustomLocalWebCSSFile();
					if (Files.exists(customLocalWebCSSFile)) {
						if (LOG.isDebugEnabled())
							LOG.debug(String.format("Setting user stylesheet at %s", customLocalWebCSSFile));
						webView.getEngine().setUserStyleSheetLocation("data:text/css;base64," +Base64.getEncoder().encodeToString(Utils.toByteArray(customLocalWebCSSFile)));
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
							.asList(String.format("%s=%s", LOCBCOOKIE, localWebServerCookie.toString())));
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
			LOG.debug(String.format("Page failed to execute uiRefresh()."), e);
		}
	}

	private void setupPage() {
		if (htmlPage == null || htmlPage.startsWith("http://") || htmlPage.startsWith("https://")) {
			pageBundle = bundle;
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
			String res = UI.class.getName();
			idx = res.lastIndexOf('.');
			String resourceName = res.substring(0, idx) + "." + base;
			if (LOG.isDebugEnabled())
				LOG.debug(String.format("Loading bundle %s", resourceName));
			try {
				pageBundle = ResourceBundle.getBundle(resourceName);
			} catch (MissingResourceException mre) {
				// Page doesn't have resources
				LOG.debug(String.format("No resources for %s", resourceName));
				pageBundle = bundle;
			}
		}
	}
    
    private void vpnAvailable() {

        LOG.info("Vpn interface now available.");
        
        Runnable runnable = new Runnable() {
            public void run() {
                if (awaitingBridgeEstablish != null) {
                    // Bridge established as result of update, now restart the
                    // client itself
                    resetAwaingBridgeEstablish();
                    setUpdateProgress(100, bundle.getString("guiRestart"));
                    new Timeline(new KeyFrame(Duration.seconds(5), ae -> uiContext.getAppContext().shutdown(true))).play();
                } else {
                    String unprocessedUri = uiContext.getAppContext().getUri();
                    if (Utils.isNotBlank(unprocessedUri) && !unprocessedUri.equals("lbvpn://")) {
                        connectToUri(unprocessedUri);
                    } else {
                        initUi();
                        selectPageForState(uiContext.getAppContext().isConnect(), false, false);
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

    private void vpnGone() {
        LOG.warn("Vpn interface lost");
        maybeRunLater(() -> {
            if (awaitingBridgeLoss != null) {
                // Bridge lost as result of update, wait for it to come back
                resetAwaingBridgeLoss();
                setUpdateProgress(100, bundle.getString("waitingStart"));
                awaitingBridgeEstablish = new Timeline(
                        new KeyFrame(Duration.seconds(30), ae -> giveUpWaitingForBridgeEstablish()));
                awaitingBridgeEstablish.play();
            } else {
                connecting.clear();
                initUi();
                selectPageForState(false, false, false);
            }
        });
    }

	private IVpnConnection getForegroundConnection() {
		if (htmlPage != null && isRemote(htmlPage)) {

		} else {
			String anchor = getAnchor();
			if (anchor != null) {
				try {
					return vpnManager.getVpnOrFail().getConnection(Long.parseLong(anchor));
				} catch (Exception nfe) {
				}
			}
		}
		return getPriorityConnection();
	}

	private void processJavascript() {
		if (LOG.isDebugEnabled())
			LOG.debug("Processing page content");
		var baseHtmlPage = getBaseHtml();
		var engine = webView.getEngine();
		var jsobj = (JSObject) engine.executeScript("window");
		jsobj.setMember("bridge", bridge);
		if(vpnManager.isBackendAvailable()) {
			try {
				jsobj.setMember("vpn", vpnManager.getVpn());
			} catch (IllegalStateException ise) {
				/* No bus */
			}
		}
		var selectedConnection = getForegroundConnection();
		jsobj.setMember("connection", selectedConnection);
		jsobj.setMember("pageBundle", pageBundle);
		jsobj.setMember("jlog", LoggerFactory.getLogger("html-" + htmlPage));

		/* Special pages */
        if ("updateAvailable.html".equals(baseHtmlPage)) {
            jsobj.setMember("automaticUpdates", vpnManager.getVpn().map(vpn -> vpn.getBooleanValue(ConfigurationItem.AUTOMATIC_UPDATES.getKey())).orElse(false));
        }
        else if ("options.html".equals(baseHtmlPage)) {
			beansForOptions = beansForOptions();
            for (var beanEn : beansForOptions.entrySet()) {
				LOG.debug(String.format(" Setting %s to %s", beanEn.getKey(), beanEn.getValue()));
				jsobj.setMember(beanEn.getKey(), beanEn.getValue());
			}
		} else if ("authorize.html".equals(baseHtmlPage)) {
			// TODO release this is page changes
			var authorizedLock = new Semaphore(1);
			try {
				authorizedLock.acquire();
			} catch (InterruptedException e1) {
				throw new IllegalStateException("Already acquired authorize lock.");
			}

			var authenticator = new ServiceClientAuthenticator<CONX>(uiContext, engine, authorizedLock);
			jsobj.setMember("authenticator", authenticator);
			var serviceClient = new ServiceClient(appContext.getCookieStore(), authenticator, appContext.getCertManager());
			jsobj.setMember("serviceClient", serviceClient);
			jsobj.setMember("register", new Register<CONX>(selectedConnection, serviceClient, this));
		}

		/* Override log. TODO: Not entirely sure this works entirely */
		if (uiContext.debugger().isEmpty()) {
			engine.executeScript("oldLog = console.log; console.log = function(message) { oldLog.apply(this, arguments); bridge.log(message); };");
		}

		/* Signal to page we are ready */
		try {
			engine.executeScript("uiReady();");
		} catch (Exception e) {
			LOG.debug(String.format("Page %s failed to execute uiReady() functions.", baseHtmlPage), e);
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
		var htmlPage = this.htmlPage;
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
		var busAvailable = vpnManager.isBackendAvailable();
		var connection = busAvailable ? getForegroundConnection() : null;
		var processor = new DOMProcessor<CONX>(uiContext, busAvailable ? vpnManager.getVpnOrFail() : null, connection,
				collections, lastErrorMessage, lastErrorCause, lastException, brandingManager.branding().map(d -> d.branding()).orElse(null), pageBundle, bundle,
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
	
	public void connectToUri(String unprocessedUri) {
        LOG.info("Connected to URI {}", unprocessedUri);
        uiContext.getAppContext().getScheduler().execute(() -> {
            try {
                LOG.info(String.format("Connected to URI %s", unprocessedUri));
                URI uriObj;
                if(unprocessedUri.startsWith("lbvpn://")) {
                    uriObj = ConnectionUtil.getUri("https://" + unprocessedUri.substring(8));
                }
                else {
                    uriObj = ConnectionUtil.getUri(unprocessedUri);
                }
                long connectionId = vpnManager.getVpnOrFail().getConnectionIdForURI(uriObj.toASCIIString());
                if (connectionId == -1) {
                    if (uiContext.getAppContext().isCreateIfDoesntExist()) {
                        /* No existing configuration */
                        vpnManager.getVpnOrFail().createConnection(uriObj.toASCIIString(), true, true,
                                Mode.CLIENT.name());
                        
                        Thread.sleep(1000);
                        var conx = vpnManager.getVpnOrFail().getConnection(connectionId);

                        if(isNotBlank(uriObj.getUserInfo())) {
                            conx.setUsernameHint(uriObj.getUserInfo());
                            conx.save();
                        }

                        if (uiContext.getAppContext().isConnectUri()) {
                            runLater(() -> connect(conx));
                        }
                    } else {
                        showError(MessageFormat.format(bundle.getString("error.uriProvidedDoesntExist"), uriObj.toASCIIString()));
                    }
                } else {
                    reloadState(() -> {
                        maybeRunLater(() -> {
                            initUi();
                            if (uiContext.getAppContext().isConnectUri()) {
                                var conx = vpnManager.getVpnOrFail().getConnection(connectionId);
                                connect(conx);
                            }
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

	private void configure(String usernameHint, String configIniFile, IVpnConnection config) {
		try {
			LOG.info(String.format("Configuration for %s on %s", usernameHint, config.getDisplayName()));
			config.setUsernameHint(usernameHint);
			String error = config.parse(configIniFile);
			if (Utils.isNotBlank(error))
				throw new IOException(error);

			config.save();
			selectPageForState(false, false, false);
			config.authorized();
		} catch (Exception e) {
			showError("Failed to configure connection.", e);
		}
	}

	private void setAsFavourite(IVpnConnection sel) {
		sel.setAsFavourite();
	}

	private boolean confirmDelete(IVpnConnection sel) {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.initModality(Modality.APPLICATION_MODAL);
		alert.initOwner(getStage());
		alert.setTitle(bundle.getString("delete.confirm.title"));
		alert.setHeaderText(bundle.getString("delete.confirm.header"));
		alert.setContentText(MessageFormat.format(bundle.getString("delete.confirm.content"), sel.getUri(false)));
		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == ButtonType.OK) {
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

	private void doDelete(IVpnConnection sel) {
		setHtmlPage("busy.html");
		LOG.info(String.format("Deleting connection %s", sel));
		uiContext.getAppContext().getScheduler().execute(() -> sel.delete());
	}

	private void reloadState(Runnable then) {
		/*
		 * Care must be take that this doesn't run on the UI thread, as it may take a
		 * while.
		 */

//		mode = context.getDBus().isBusAvailable() && context.getDBus().getVPN().isUpdating() ? UIState.UPDATE : UIState.NORMAL;
		var favouriteConnection = getFavouriteConnection();
		if (uiContext.isAllowBranding()) {
	        brandingManager.apply(favouriteConnection);
		}

		then.run();
	}

	private void editConnection(IVpnConnection connection) {
		setHtmlPage("editConnection.html#" + connection.getId());
	}
	
	public BrandingManager<CONX> getBrandingManager() {
	    return brandingManager;
	}

	public void back() {
		var busAvailable = vpnManager.isBackendAvailable();
		if (busAvailable) {
			for (var c : vpnManager.getVpnOrFail().getConnections()) {
				if (c.getStatus().equals(ConnectionStatus.Type.AUTHORIZING.name())) {
					c.disconnect(bundle.getString("cancelled"));
					selectPageForState(false, false, false);
					return;
				}
			}
			if (updateService.isUpdatesEnabled() && updateService.isNeedsUpdating()) {
				updateService.deferUpdate();
				return;
			}
		}
		WebHistory history = webView.getEngine().getHistory();
		if (history.getCurrentIndex() > 0)
			history.go(-1);
	}

	@FXML
	@Reflectable
	private void evtAddConnection() {
		addConnection();
	}

	@FXML
	@Reflectable
	private void evtLaunchDebugger() {
		uiContext.debugger().ifPresent(d -> d.launch());
	}

	@FXML
	@Reflectable
	private void evtStartDebugger() {
		uiContext.debugger().orElseThrow().startDebugging((ex) -> {
			// failed to start
			LOG.error("Debug server failed to start: " + ex.getMessage());
			debuggerLink.textProperty().set("");
			startDebugger.setDisable(false);
//             updateDebugOff.run();
		}, () -> {
			LOG.info("Debug server started, debug URL: " + uiContext.debugger().get().getDebuggerURL());
			debuggerLink.textProperty().set("Launch Chrome Debugger");
			startDebugger.setDisable(true);
			// can copy debug URL to clipboard
//             updateDebugOn.run();
		});
//		startDebugging(null, null);
	}

	@FXML
	@Reflectable
	private void evtStopDebugger() {
		uiContext.debugger().orElseThrow().stopDebugging((e) -> {
			startDebugger.setDisable(false);
			debuggerLink.textProperty().set("");
		});
	}

	@FXML
	@Reflectable
	private void evtOptions() throws Exception {
		options();
	}

	private void giveUpWaitingForBridgeEstablish() {
		LOG.info("Given up waiting for bridge to start");
		resetAwaingBridgeEstablish();
		selectPageForState(false, false, false);
	}

	private void initUi() {
		LOG.info("Rebuilding URIs");
		try {
		} catch (Exception e) {
			LOG.error("Failed to load connections.", e);
		}
	}

	private List<CONX> getAllConnections() {
		return vpnManager.isBackendAvailable() ? vpnManager.getVpn().map(vpn -> Arrays.asList(vpn.getConnections())).orElse(Collections.emptyList()) : Collections.emptyList();
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

	private IVpnConnection getAuthorizingConnection() {
		return getFirstConnectionForState(Type.AUTHORIZING);
	}

	private IVpnConnection getConnectingConnection() {
		return getFirstConnectionForState(Type.CONNECTING);
	}

	private IVpnConnection getFirstConnectionForState(Type... type) {
		if (vpnManager.isBackendAvailable()) {
			List<Type> tl = Arrays.asList(type);
			for (var connection : vpnManager.getVpn().map(vpn -> Arrays.asList(vpn.getConnections())).orElse(Collections.emptyList())) {
				Type status = Type.valueOf(connection.getStatus());
				if (tl.contains(status)) {
					return connection;
				}
			}
		}
		return null;
	}

	private void selectPageForState(boolean connectIfDisconnected, boolean force, boolean options) {
		try {
			var busAvailable = vpnManager.isBackendAvailable();
			if (!busAvailable) {
				/* The bridge is not (yet?) connected */
				setHtmlPage("index.html");
			} else {

				/* Are ANY connections authorizing? */
				var connection = getAuthorizingConnection();
				if (connection != null) {
				    Mode mode = Mode.valueOf(connection.getMode());
                    if (mode == Mode.MODERN) {
                        LOG.info("Authorizing modern");
                        switch(AuthMethod.select(true, connection.getAuthMethods())) {
                        case DEVICE_TOKEN:
                            setHtmlPage("authorize.html");
                            break;
                        case EMBEDDED_BROWSER:
                            String authUrl = connection.getUri(false);
                            if(authUrl.endsWith("/app")) {
                                authUrl = authUrl.substring(0, authUrl.length() - 4) + "/vpn";
                            }
                            
                            LOG.info(String.format("Authorizing to %s", authUrl));
                            if(authUrl.contains("?"))
                                authUrl += "&";
                            else
                                authUrl += "?";
                            authUrl += "pubkey=" + URLEncoder.encode(connection.getUserPublicKey(), "UTF-8");
                            authUrl += "&instance=" + URLEncoder.encode(connection.getInstance(), "UTF-8");
                            authUrl += "&name=" + URLEncoder.encode(Util.getDeviceName(), "UTF-8");
                            authUrl += "&os=" + URLEncoder.encode(OsUtil.getOS().toUpperCase(), "UTF-8");
                            setHtmlPage(authUrl);
                            break;
                        default:
                            throw new UnsupportedOperationException();
                        }
                    }
                    else if (mode == Mode.SERVICE) {
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
					if (vpnManager.getVpn().map(vpn -> vpn.getConnections().length == 0).orElse(true)) {
						if (uiContext.getAppContext().isNoAddWhenNoConnections()) {
							if (uiContext.getAppContext().isConnect()
									|| Utils.isNotBlank(uiContext.getAppContext().getUri()))
								setHtmlPage("busy.html");
							else
								setHtmlPage("connections.html");
						} else
							setHtmlPage("addLogonBoxVPN.html");
					} else if(options) {
                        setHtmlPage("options.html");
                    } else {
						setHtmlPage("connections.html");
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
				var document = webView.getEngine().getDocument();
				if (document == null) {
					LOG.warn(String.format("No document,  ignoring progress update '%s'.", text));
				} else {
					var progressElement = document.getElementById("progress");
					progressElement.setAttribute("aria-valuenow", String.valueOf(val));
					progressElement.setAttribute("style", "width: " + String.valueOf(val) + "%");
					
					var progressText = document.getElementById("progressText");
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

	void showError(String error, Throwable exception) {
		showError(error, null, exception);
	}

	private void showError(String error, String cause, String exception) {
		if(cause != null && cause.startsWith("No subject alternative")) {
			showError(error, cause, exception, "sanError.html");
		}
		else
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
			if(cause != null && cause.startsWith("No subject alternative")) {
				setHtmlPage("sanError.html");
			}
			else {
				setHtmlPage("error.html");
			}
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
	    uiContext.getAppContext().getScheduler().execute(() -> {
	        try {
	            checkForUpdate.set(true);
	            try {
	                updateService.checkForUpdate();
	            }
	            finally {
	                checkForUpdate.remove();
	            }
	            maybeRunLater(() -> {
	                if (updateService.isNeedsUpdating() && getAuthorizingConnection() == null) {
	                    LOG.info("Updated needed and no authorizing connections, checking page state.");
	                    selectPageForState(false, true, false);
	                }
	                else {
	                    webView.getEngine().executeScript("noUpdates();");
	                }
	            });
	        } catch (IOException ioe) {
	            maybeRunLater(() -> {
	                showError("Failed to check for updates.", ioe);
	            });
	        }    
	    });
		
	}

	private void deferUpdate() {
		updateService.deferUpdate();
		selectPageForState(false, true, false);
	}

	public static void maybeRunLater(Runnable r) {
		if (Platform.isFxApplicationThread())
			r.run();
		else
			Platform.runLater(r);
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
		uiContext.debugger().ifPresent(d -> d.pageReloading());
		// load the web page
		webView.getEngine().load(url);
		LOG.info("Loaded " + url);
	}
}
