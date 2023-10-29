package com.logonbox.vpn.client.gui.swt;

import static com.logonbox.vpn.client.common.Utils.defaultIfBlank;
import static com.logonbox.vpn.client.common.Utils.isBlank;
import static com.logonbox.vpn.client.common.Utils.isNotBlank;

import com.logonbox.vpn.client.common.AuthenticationCancelledException;
import com.logonbox.vpn.client.common.BrandingManager;
import com.logonbox.vpn.client.common.BrandingManager.ImageHandler;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.ConfigurationItem.TrayMode;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.ConnectionUtil;
import com.logonbox.vpn.client.common.ServiceClient;
import com.logonbox.vpn.client.common.ServiceClient.NameValuePair;
import com.logonbox.vpn.client.common.UpdateService;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.common.lbapi.InputField;
import com.logonbox.vpn.client.common.lbapi.LogonResult;
import com.sshtools.liftlib.OS;

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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;

import jakarta.json.JsonObject;
import netscape.javascript.JSObject;
import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class UI2 {

	static ResourceBundle BUNDLE = ResourceBundle.getBundle(UI2.class.getName());

	public static final class ServiceClientAuthenticator implements ServiceClient.Authenticator {
        private final VpnManager<VpnConnection> vpnManager;
        private LogonResult result;
        private boolean error;
        private final Browser engine;
        private final Semaphore authorizedLock;
        private final Client context;
        private Map<InputField, NameValuePair> results;
        private Display display;
        private Main main;

        public ServiceClientAuthenticator(Main main, Display display, Client context, Browser engine,
                Semaphore authorizedLock) {
        
            this.engine = engine;
            this.context = context;
            this.authorizedLock = authorizedLock;
            
            vpnManager = context.getApp().getVpnManager();
        }

        public void cancel() {
            LOG.info("Cancelling authorization.");
            error = true;
            authorizedLock.release();
        }

        public void submit(JSObject obj) {
            for (var field : result.formTemplate().inputFields()) {
                results.put(field, new NameValuePair(field.resourceKey(),
                        memberOrDefault(obj, field.resourceKey(), String.class, "")));
            }
            authorizedLock.release();
        }

        @Override
        public String getUUID() {
            return vpnManager.getVpnOrFail().getUUID();
        }

        @Override
        public HostnameVerifier getHostnameVerifier() {
            return context.getApp().getCertManager();
        }

        @Override
        public void error(JsonObject i18n, LogonResult logonResult) {
            maybeRunLater(display, () -> {
                if (logonResult.lastErrorIsResourceKey())
                    engine.evaluate("authorizationError('"
                            + Utils.escapeEcmaScript(i18n.getString(logonResult.errorMsg())) + "');", true);
                else
                    engine.evaluate("authorizationError('"
                            + Utils.escapeEcmaScript(logonResult.errorMsg()) + "');" , true);

            });
        }

        @Override
        public void collect(JsonObject i18n, LogonResult result, Map<InputField, NameValuePair> results)
                throws IOException {
            this.results = results;
            this.result = result;

            maybeRunLater(display, () -> {
                // TODO
                throw new UnsupportedOperationException("TODO");
//                JSObject jsobj = (JSObject) engine.executeScript("window");
//                jsobj.setMember("remoteBundle", i18n);
//                jsobj.setMember("result", result);
//                jsobj.setMember("results", results);
//                engine.executeScript("collect();");
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
		private final VpnConnection selectedConnection;
		private final ServiceClient serviceClient;
		private final UI2 ui;
		private final Display display;

		public Register(Display display, VpnConnection selectedConnection, ServiceClient serviceClient, UI2 ui) {
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

	final static ResourceBundle bundle = ResourceBundle.getBundle(UI2.class.getName());

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

	final static Logger LOG = LoggerFactory.getLogger(UI2.class);

	private final List<VpnConnection> connecting = new ArrayList<>();
//	private String lastErrorMessage;
//	private String lastErrorCause;
//	private String lastException;
	private Runnable runOnNextLoad;
	private ServerBridge2 bridge;
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

    protected URL location;
    protected Composite scene;
//  private RotateTransition loadingRotation;

    private Composite stack;
    private StackLayout stackLayout;
    private Composite top;

    private final VpnManager<VpnConnection> vpnManager;
    private final UpdateService updateService;
    private final Shell shell;
    private final Display display;
    private final Main main;
    private final PageModel2 pageModel;
    private final Client context;

    private final BrandingManager<VpnConnection, Image> brandingManager;

	public UI2(Shell shell, Client context, Main main, Display display) {
		this.shell = shell;
		this.main = main;
		this.display = display;
		this.context = context;

		vpnManager = main.getVpnManager();
		updateService = main.getUpdateService();
		pageModel = new PageModel2(context, updateService, BUNDLE);
		
		brandingManager = new BrandingManager<>(vpnManager, new ImageHandler<Image>() {

            @Override
            public Image create(int width, int height, String color) {
//                BufferedImage bim = null;
//                bim = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
//                var graphics = (java.awt.Graphics2D) bim.getGraphics();
//                graphics.setColor(java.awt.Color.decode(color));
//                graphics.fillRect(0, 0, width, height);
//                return bim;
                return null;
            }

            @Override
            public void draw(Image bim, Path logoFile) {
//                var graphics = (java.awt.Graphics2D) bim.getGraphics();
//                LOG.info(String.format("Drawing logo on splash"));
//                try {
//                    var logoImage = ImageIO.read(logoFile.toFile());
//                    if (logoImage == null)
//                        throw new IOException(String.format("Failed to load image from %s", logoFile));
//                    graphics.drawImage(logoImage, (bim.getWidth() - logoImage.getWidth()) / 2,
//                            (bim.getHeight() - logoImage.getHeight()) / 2, null);
//                }
//                catch(IOException ioe) {
//                    throw new UncheckedIOException(ioe);
//                }
            }

            @Override
            public void write(Image bim, Path splashFile) throws IOException {
//                ImageIO.write(bim, "png", splashFile.toFile());
//                LOG.info(String.format("Custom splash written to %s", splashFile));
            }
        });
	}
	
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

		if (OS.isMacOs()) {
			if (!main.isNoClose() && !main.isWindowDecorations()) {
				close = new FlatButton(top, SWT.PUSH);
				close.setLayoutData(bg);
				close.setImage(FontIcon.image(display, FontAwesome.WINDOW_CLOSE, 32));
			}
			if (!main.isNoMinimize() && context.isMinimizeAllowed() && !main.isWindowDecorations()) {
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

			if (!main.isNoMinimize() && context.isMinimizeAllowed() && !main.isWindowDecorations()) {
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
				var busAvailable = vpnManager.isBackendAvailable();
				if (busAvailable) {
					for (var c : vpnManager.getVpn().map(vpn -> vpn.getConnections()).orElse(Collections.emptyList())) {
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

		bridge = new ServerBridge2(this, context);
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
		    vpnManager.onVpnGone(this::vpnGone);
		    vpnManager.onConnectionAdded((conx) -> {
		        maybeRunLater(display, () -> {
		            context.getOpQueue().execute(() -> {
                        reloadState(() -> {
                            maybeRunLater(display, () -> {
                                reapplyColors();
                                reapplyLogo();
                                if (conx.isAuthorized())
                                    joinNetwork(conx);
                                else
                                    authorize(conx);
                            });
                        });
                    });
                });
		    });
		    vpnManager.onConnectionRemoved(id -> {
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
		    });
		    vpnManager.onConfirmedExit(() -> context.exitApp());
		    vpnManager.onConnectionUpdated(conx -> {
                maybeRunLater(display, () -> {
                    selectPageForState(false, false);
                });
		    });
		    vpnManager.onGlobalConfigChanged((item, val) -> {
                if (item.equals(ConfigurationItem.FAVOURITE)) {
                    reloadState(() -> {
                        maybeRunLater(display, () -> {
                            initUi();
                            refresh();
                        });
                    });
                }
		    });
		    vpnManager.onVpnAvailable(() -> {
		        LOG.info("Bridge established.");

		        /*
		         * If we were waiting for this, it's part of the update process. We don't want
		         * the connection continuing
		         */
		        reloadState(() -> maybeRunLater(display, () -> {
		            var unprocessedUri = main.getUri();
		            if (isNotBlank(unprocessedUri)) {
		                connectToUri(unprocessedUri);
		            } else {
		                initUi();
		                selectPageForState(main.isConnect(), false);
		            }
		        }));
		    });
		    vpnManager.onAuthorize((conx,uri,mode) -> {
		        pageModel.setDisconnectionReason(null);
                if (mode.equals(Connection.Mode.CLIENT) || mode.equals(Connection.Mode.SERVICE)) {
                    maybeRunLater(display, () -> {
                        // setHtmlPage(connection.getUri(false) + sig.getUri());
                        selectPageForState(false, false);
                    });
                } else {
                    LOG.info("This client doest not handle the authorization mode '{}'", mode);
                }
		    });
		    vpnManager.onConnecting(conx -> {
		        pageModel.setDisconnectionReason(null);
                maybeRunLater(display, () -> {
                    selectPageForState(false, false);
                });
		    });
		    vpnManager.onConnected(conx -> {
		        maybeRunLater(display, () -> {
                    connecting.remove(conx);
                    if (main.isExitOnConnection()) {
                        context.exitApp();
                    } else
                        selectPageForState(false, true);
                });
		    });
		    vpnManager.onFailure((conx,message,cause,trace) -> {
		        maybeRunLater(display, () -> {
                    LOG.info("Failed to connect. {}", message);
                    connecting.remove(conx);
                    showError("Failed to connect.", message, trace);
                    uiRefresh();
                });
		    });
		    vpnManager.onTemporarilyOffline((conx,reason) -> {
		        pageModel.setDisconnectionReason(reason);
                maybeRunLater(display, () -> {
                    LOG.info("Temporarily offline {}", conx.getId());
                    selectPageForState(false, false);
                });
		    });
		    vpnManager.onDisconnected((conx,reason) -> {
                maybeRunLater(display, () -> {
                    LOG.info("Disconnected {}", conx.getId());
                    selectPageForState(false, false);
                });
		    });
            vpnManager.onDisconnecting((conx,reason) -> {
                maybeRunLater(display, () -> {
                    LOG.info("Disconnecting {}", conx.getId());
                    selectPageForState(false, false);
                });
            });
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

	public void disconnect(VpnConnection sel) {
		disconnect(sel, null);
	}

	public void disconnect(VpnConnection sel, String reason) {
		if (sel == null)
			throw new IllegalArgumentException("No connection given.");
		if (reason == null)
			LOG.info("Requesting disconnect, no reason given");
		else
			LOG.info(String.format("Requesting disconnect, because '%s'", reason));

		context.getOpQueue().execute(() -> {
			try {
				sel.disconnect(defaultIfBlank(reason, ""));
			} catch (Exception e) {
				display.asyncExec(() -> showError("Failed to disconnect.", e));
			}
		});
	}

	public void connect(VpnConnection n) {
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
		var dbus = context.getApp();
		var vpnManager = dbus.getVpnManager();
		vpnManager.getVpn().ifPresentOrElse(vpn -> {
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
		}, () -> {
		    beans.put("phases", new String[0]);
            beans.put("phase", "");
            beans.put("automaticUpdates", "true");
            beans.put("ignoreLocalRoutes", "true");
            beans.put("dnsIntegrationMethod", "AUTO");
            beans.put("trayMode", TrayMode.AUTO.name());
		});

		/* Option collections */
		beans.put("trayModes", Arrays.asList(TrayMode.values()).stream().map(TrayMode::name)
				.collect(Collectors.toUnmodifiableList()).toArray(new String[0]));
		beans.put("darkModes", new String[] { Configuration.DARK_MODE_AUTO, Configuration.DARK_MODE_ALWAYS,
				Configuration.DARK_MODE_NEVER });
        beans.put("logLevels", Arrays.asList(Level.values()).stream().map(Level::toString).collect(Collectors.toList()).toArray(new String[0]));
        beans.put("dnsIntegrationMethods", ConfigurationItem.DNS_INTEGRATION_METHOD.getValues().toArray(new String[0]));

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

	private void vpnGone() {
		LOG.info("Bridge lost");
		maybeRunLater(display, () -> {
			connecting.clear();
			initUi();
			selectPageForState(false, false);
		});
	}

	protected void joinNetwork(VpnConnection connection) {
		context.getOpQueue().execute(() -> {
			connection.connect();
		});
	}

	protected void addConnection(Boolean stayConnected, Boolean connectAtStartup, String unprocessedUri, Mode mode)
			throws URISyntaxException {
		var uriObj = ConnectionUtil.getUri(unprocessedUri);
		context.getOpQueue().execute(() -> {
			try {
				vpnManager.getVpnOrFail().createConnection(uriObj.toASCIIString(), connectAtStartup, stayConnected,
						mode.name());
			} catch (Exception e) {
				showError("Failed to add connection.", e);
			}
		});
	}

	protected void importConnection(Path file) throws IOException {
		try (var r = Files.newBufferedReader(file)) {
			var content = Utils.toString(r);
			context.getOpQueue().execute(() -> {
				try {
					vpnManager.getVpnOrFail().importConfiguration(content);
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

	protected void authorize(VpnConnection n) {
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
//			VpnConnection priorityConnection = getPriorityConnection();
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
			VpnConnection connection) {
		try {
			var uriObj = ConnectionUtil.getUri(server);

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

	protected VpnConnection getFavouriteConnection() {
		return getFavouriteConnection(getAllConnections());
	}

	protected VpnConnection getFavouriteConnection(Collection<VpnConnection> list) {
		var fav = list.isEmpty() ? 0l : vpnManager.getVpnOrFail().getLongValue(ConfigurationItem.FAVOURITE.getKey());
		for (var connection : list) {
			if (list.size() == 1 || connection.getId() == fav)
				return connection;
		}
		return list.isEmpty() ? null : list.iterator().next();
	}

	protected VpnConnection getPriorityConnection() {
		List<VpnConnection> alls = getAllConnections();
		if (alls.isEmpty())
			return null;

		/*
		 * New UI, we use the connection that needs the most attention. If / when it
		 * becomes the default, then this will not be necessary.
		 */
		for (VpnConnection connection : alls) {
			ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
			if (type == Type.AUTHORIZING) {
				return connection;
			}
		}

		for (VpnConnection connection : alls) {
			ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
			if (type == Type.CONNECTING || type == Type.DISCONNECTING) {
				return connection;
			}
		}

		for (VpnConnection connection : alls) {
			ConnectionStatus.Type type = Type.valueOf(connection.getStatus());
			if (type == Type.CONNECTED) {
				return connection;
			}
		}

		return alls.get(0);
	}

	public void setAvailable() {
		var isNoBack = "missingSoftware.html".equals(pageModel.getHtmlPage())
				|| "connections.html".equals(pageModel.getHtmlPage()) || !vpnManager.isBackendAvailable()
				|| ("addLogonBoxVPN.html".equals(pageModel.getHtmlPage())
						&& getAllConnections().isEmpty());
		back.setVisible(!isNoBack);
		top.redraw();

	}

	protected void saveOptions(String trayMode, String darkMode, String phase, Boolean automaticUpdates,
			String logLevel, Boolean ignoreLocalRoutes, String dnsIntegrationMethod, Boolean saveCookies) {
		try {
			/* Local per-user GUI specific configuration */
			var config = Configuration.getDefault();

			if (darkMode != null)
				config.darkModeProperty(darkMode);

			if (logLevel != null) {
				config.logLevelProperty(logLevel);
				if (logLevel.length() == 0)
					main.getLogging().setLevel(main.getLogging().getDefaultLevel());
				else {
				    try { 
				        main.getLogging().setLevel(Level.valueOf(logLevel));
                        }
                    catch(IllegalArgumentException iae) {
                        main.getLogging().setLevel(main.getLogging().getDefaultLevel());
                    }
				}
			}

			if (saveCookies != null) {
				config.saveCookiesProperty(saveCookies);
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
				UI2.this.reapplyColors();
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
								VpnManager.DEVICE_IDENTIFIER, vpnManager.getVpnOrFail().getUUID())));
						cookieHandler.put(uri.resolve("/"), headers);
					} catch (Exception e) {
						throw new IllegalStateException("Failed to set cookie.", e);
					}
					pageModel.setUserStyleSheetLocation(UI2.class.getResource("remote.css").toExternalForm());
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
				    var customLocalWebCSSFile = context.getCustomLocalWebCSSFile();
					if (customLocalWebCSSFile.exists()) {
						if (LOG.isDebugEnabled())
							LOG.debug(String.format("Setting user stylesheet at %s", customLocalWebCSSFile));
						var localCss = Utils.toByteArray(customLocalWebCSSFile.toURI().toURL());
						var datauri = "data:text/css;base64," + Base64.getEncoder().encodeToString(localCss);
						pageModel.setUserStyleSheetLocation(datauri);
					} else {
						if (LOG.isDebugEnabled())
							LOG.debug(String.format("No user stylesheet at %s", customLocalWebCSSFile));
						pageModel.setUserStyleSheetLocation(null);
					}

					setupPage();
					String loc = htmlPage;
					URL resource = UI2.class.getResource(pageModel.getBaseHtml());
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

	VpnConnection getForegroundConnection() {
		if (pageModel.getHtmlPage() != null && pageModel.isRemote()) {

		} else {
			String anchor = pageModel.getAnchor();
			if (anchor != null) {
				try {
					return vpnManager.getVpnOrFail().getConnection(Long.parseLong(anchor));
				} catch (Exception nfe) {
				}
			}
		}
		return getPriorityConnection();
	}

	private void processDOM(URL url, Document doc, boolean workOnDocument) {
		if (workOnDocument)
			LOG.info("Processing DOM, output to DOM on thread " + Thread.currentThread().getName());
		else
			LOG.info("Processing DOM, output as Javascript on thread " + Thread.currentThread().getName());
		pageModel.setDocument(doc);
		pageModel.setUrl(url);
		pageModel.setConnection(getForegroundConnection());
		LOG.info("Setup DOM processing");
		pageModel.process(workOnDocument);
		LOG.info("Processed DOM");
	}

	private void connectToUri(String unprocessedUri) {
		context.getOpQueue().execute(() -> {
			try {
				LOG.info(String.format("Connected to URI %s", unprocessedUri));
				var uriObj = ConnectionUtil.getUri(unprocessedUri);
				long connectionId = vpnManager.getVpnOrFail().getConnectionIdForURI(uriObj.toASCIIString());
				if (connectionId == -1) {
					if (main.isCreateIfDoesntExist()) {
						/* No existing configuration */
					    vpnManager.getVpnOrFail().createConnection(uriObj.toASCIIString(), true, true,
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

	void configure(String usernameHint, String configIniFile, VpnConnection config) {
		try {
			LOG.info(String.format("Configuration for %s on %s", usernameHint, config.getDisplayName()));
			config.setUsernameHint(usernameHint);
			String error = config.parse(configIniFile);
			if (isNotBlank(error))
				throw new IOException(error);

			config.save();
			selectPageForState(false, false);
			config.authorized();
		} catch (Exception e) {
			showError("Failed to configure connection.", e);
		}
	}

	void setAsFavourite(VpnConnection sel) {
		sel.setAsFavourite();
	}

	boolean confirmDelete(VpnConnection sel) {
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

	private void doDelete(VpnConnection sel) throws RemoteException {
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
		VpnConnection favouriteConnection = getFavouriteConnection();
		if (Client.allowBranding) {
            brandingManager.apply(favouriteConnection);
            pageModel.setBranding(brandingManager.branding().map(b -> b.branding()).orElse(null));
		}
		brandingManager.apply(favouriteConnection);

		then.run();
	}

	private void reapplyColors() {
		context.applyColors(pageModel.getBranding(), null);
	}

	private void reapplyLogo() {

		// TODO dispose images
		var defaultLogo = UI2.class.getResource("logonbox-titlebar-logo.png");
		var branding = pageModel.getBranding();
		if ((branding == null
				|| isBlank(branding.logo()) && !defaultLogo.equals(titleBarImageViewUrl))) {
			titleBarImageView.setImage(Images.scaleToHeight(loadImage(titleBarImageViewUrl = defaultLogo), 32));
			titleBarImageView.pack();
		} else if (branding != null && !defaultLogo.toExternalForm().equals(branding.logo())
				&& !isBlank(branding.logo())) {
			try {
				titleBarImageView.setImage(
						(Images.scaleToHeight(loadImage(titleBarImageViewUrl = new URL(branding.logo())), 32)));
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

	void editConnection(VpnConnection connection) {
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

	List<VpnConnection> getAllConnections() {
		return context.getApp().getVpnManager().getVpn().map(vpn -> vpn.getConnections()).orElse(Collections.emptyList());
	}

	private VpnConnection getAuthorizingConnection() {
		return getFirstConnectionForState(Type.AUTHORIZING);
	}

	private VpnConnection getConnectingConnection() {
		return getFirstConnectionForState(Type.CONNECTING);
	}

	private VpnConnection getFirstConnectionForState(Type... type) {
		if (context.getApp().getVpnManager().isBackendAvailable()) {
			var tl = Arrays.asList(type);
			for (var connection : getAllConnections()) {
				var status = Type.valueOf(connection.getStatus());
				if (tl.contains(status)) {
					return connection;
				}
			}
		}
		return null;
	}

	void selectPageForState(boolean connectIfDisconnected, boolean force) {
		try {
			var bridge = context.getApp();
			var vpnManager = bridge.getVpnManager();
			boolean busAvailable = vpnManager.isBackendAvailable();
			if (!busAvailable) {
				/* The bridge is not (yet?) connected */
				setHtmlPage("index.html");
			} else {

				/* Are ANY connections authorizing? */
				VpnConnection connection = getAuthorizingConnection();
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
					if (getAllConnections().isEmpty()) {
						if (main.isNoAddWhenNoConnections()) {
							if (main.isConnect() || isNotBlank(main.getUri()))
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
		var fontFile = UI2.class.getResource(resourceName);
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
}
