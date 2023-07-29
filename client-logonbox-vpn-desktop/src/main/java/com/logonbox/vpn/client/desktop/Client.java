package com.logonbox.vpn.client.desktop;

import com.goxr3plus.fxborderlessscene.borderless.BorderlessScene;
import com.jthemedetecor.OsThemeDetector;
import com.logonbox.vpn.client.gui.jfx.AppContext;
import com.logonbox.vpn.client.gui.jfx.Configuration;
import com.logonbox.vpn.client.gui.jfx.Debugger;
import com.logonbox.vpn.client.gui.jfx.Navigator;
import com.logonbox.vpn.client.gui.jfx.PowerMonitor;
import com.logonbox.vpn.client.gui.jfx.PowerMonitor.Listener;
import com.logonbox.vpn.client.gui.jfx.Styling;
import com.logonbox.vpn.client.gui.jfx.Tray;
import com.logonbox.vpn.client.gui.jfx.UI;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.api.Branding;
import com.sshtools.twoslices.ToasterFactory;
import com.sshtools.twoslices.ToasterSettings.SystemTrayIconMode;
import com.sshtools.twoslices.impl.JavaFXToaster;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.PropertyConfigurator;
import org.freedesktop.dbus.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.SplashScreen;
import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Client extends Application implements Listener, UIContext {

	static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true").equals("true");

	public static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());
	static Logger log = LoggerFactory.getLogger(Client.class);
	public static Alert createAlertWithOptOut(AlertType type, String title, String headerText, String message,
			String optOutMessage, Consumer<Boolean> optOutAction, ButtonType... buttonTypes) {
		Alert alert = new Alert(type);
		alert.getDialogPane().applyCss();
		Node graphic = alert.getDialogPane().getGraphic();
		alert.setDialogPane(new DialogPane() {
			@Override
			protected Node createDetailsButton() {
				CheckBox optOut = new CheckBox();
				optOut.setText(optOutMessage);
				optOut.setOnAction(e -> optOutAction.accept(optOut.isSelected()));
				autosize();
				return optOut;
			}
		});
		alert.getDialogPane().getButtonTypes().addAll(buttonTypes);
		alert.getDialogPane().setContentText(message);
		alert.getDialogPane().setExpandableContent(new Group());
		alert.getDialogPane().setExpanded(true);
		alert.getDialogPane().setGraphic(graphic);
		alert.setTitle(title);
		alert.setHeaderText(headerText);
		alert.getDialogPane().autosize();
		return alert;
	}

	private CookieHandler originalCookieHander;

	private Optional<Debugger> debugger = Optional.empty();

	private Branding branding;

	private OsThemeDetector detector;

	private ExecutorService opQueue = Executors.newSingleThreadExecutor();
	private Stage primaryStage;
	private Tray tray;
	private boolean waitingForExitChoice;
	private UI ui;
	private Main app;
	private Styling styling;
	private TitleBar titleBar;

	public Client() {
		app = Main.getInstance().uiContext(this);
	}

	public void applyColors(Branding branding, Parent node) {
		if (node == null && ui != null)
			node = ui.getScene().getRoot();
		
		if(node == null) {
			return;
		}

		this.branding = branding;

		ObservableList<String> ss = node.getStylesheets();

		/* No branding, remove the custom styles if there are any */
		ss.clear();

		/* Create new custom local web styles */
		styling.writeLocalWebCSS(branding);

		/* Create new JavaFX custom styles */
		styling.writeJavaFXCSS(branding);
		File tmpFile = styling.getCustomJavaFXCSSFile();
		if (log.isDebugEnabled())
			log.debug(String.format("Using custom JavaFX stylesheet %s", tmpFile));
		var uri = Styling.toUri(tmpFile).toExternalForm();
		ss.add(0, uri);

		var settings = ToasterFactory.getSettings();
		var properties = settings.getProperties();
//        settings.setParent(getStage());
		settings.setAppName(BUNDLE.getString("appName"));
		settings.setSystemTrayIconMode(SystemTrayIconMode.HIDDEN);
		var css = Client.class.getResource(Client.class.getSimpleName() + ".css").toExternalForm();
		if(SystemUtils.IS_OS_MAC_OSX) {
			settings.setPreferredToasterClassName(JavaFXToaster.class.getName());
		}
		properties.put(JavaFXToaster.DARK, isDarkMode());
		properties.put(JavaFXToaster.STYLESHEETS, Arrays.asList(uri, css));
		properties.put(JavaFXToaster.COLLAPSE_MESSAGE, BUNDLE.getString("collapse"));
		properties.put(JavaFXToaster.THRESHOLD, 6);
		ss.add(css);

	}
	
	@Override
	public void back() {
		ui.back();
	}

	public void clearLoadQueue() {
		opQueue.shutdownNow();
		opQueue = Executors.newSingleThreadExecutor();
	}

	public void confirmExit() {
		int active = 0;
		try {
			active = getDBus().getVPN().getActiveButNonPersistentConnections();
		} catch (Exception e) {
			exitApp();
		}

		if (active > 0) {
			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.initModality(Modality.APPLICATION_MODAL);
			alert.initOwner(getStage());
			alert.setTitle(BUNDLE.getString("exit.confirm.title"));
			alert.setHeaderText(BUNDLE.getString("exit.confirm.header"));
			alert.setContentText(BUNDLE.getString("exit.confirm.content"));

			ButtonType disconnect = new ButtonType(BUNDLE.getString("exit.confirm.disconnect"));
			ButtonType stayConnected = new ButtonType(BUNDLE.getString("exit.confirm.stayConnected"));
			ButtonType cancel = new ButtonType(BUNDLE.getString("exit.confirm.cancel"), ButtonData.CANCEL_CLOSE);

			alert.getButtonTypes().setAll(disconnect, stayConnected, cancel);
			waitingForExitChoice = true;
			try {
				Optional<ButtonType> result = alert.showAndWait();

				if (result.get() == disconnect) {
					opQueue.execute(() -> {
						getDBus().getVPN().disconnectAll();
						exitApp();
					});
				} else if (result.get() == stayConnected) {
					exitApp();
				}
			} finally {
				waitingForExitChoice = false;
			}
		} else {
			exitApp();
		}
	}

	@Override
	public Optional<Debugger> debugger() {
		return debugger;
	}

	@Override
	public void exitApp() {
		app.exit();
		opQueue.shutdown();
		Platform.exit();
	}

	@Override
	public AppContext getAppContext() {
		return app;
	}

	@Override
	public AbstractDBusClient getDBus() {
		return app;
	}

	@Override
	public ExecutorService getOpQueue() {
		return opQueue;
	}

	public Stage getStage() {
		return primaryStage;
	}

	public Tray getTray() {
		return tray;
	}

	@Override
	public void init() throws Exception {
		styling = new Styling(this);
		titleBar = new TitleBar(this);
		if(Boolean.getBoolean("logonbox.vpn.chromeDebug")) {
		    debugger = Optional.of(ServiceLoader.load(Debugger.class).findFirst().orElseThrow(() -> new IllegalStateException("Debugging requested, but not debugger module on classpath / modulepath")));
		}
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				cleanUp();
			}
		});

		Platform.setImplicitExit(false);
		PropertyConfigurator.configureAndWatch(
				System.getProperty("hypersocket.logConfiguration", "conf" + File.separator + "log4j.properties"));
	}

	@Override
	public boolean isAllowBranding() {
		return allowBranding;
	}

	@Override
	public boolean isContextMenuAllowed() {
		return debugger().isPresent();
	}

	public boolean isDarkMode() {
		String mode = Configuration.getDefault().darkModeProperty().get();
		if (mode.equals(Configuration.DARK_MODE_AUTO))
			return detector.isDark();
		else if (mode.equals(Configuration.DARK_MODE_ALWAYS))
			return true;
		else
			return false;
	}

	public boolean isMinimizeAllowed() {
		return tray == null || tray instanceof AWTTaskbarTray;
	}

	@Override
	public boolean isTrayConfigurable() {
		return tray != null && tray.isConfigurable();
	}

	public boolean isUndecoratedWindow() {
		return System.getProperty("logonbox.vpn.undecoratedWindow", "true").equals("true");
	}

	public boolean isWaitingForExitChoice() {
		return waitingForExitChoice;
	}

	public void maybeExit() {
		if (tray == null || !tray.isActive()) {
			confirmExit();
		} else {
			Platform.runLater(() -> primaryStage.hide());
		}
	}

	@Override
	public void minimize() {
		getStage().setIconified(true);
	}

	@Override
	public Navigator navigator() {
		return titleBar;
	}

	public void open() {
		log.info("Open request");
		UI.maybeRunLater(() -> {
			if (primaryStage.isIconified())
				primaryStage.setIconified(false);
			primaryStage.show();
			primaryStage.toFront();
		});
	}

	@Override
	public void openURL(String url) {
		getHostServices().showDocument(url);
	}

	public void options() {
		open();
		Platform.runLater(() -> ui.options());
	}

	public boolean promptForCertificate(AlertType alertType, String title, String content, String key,
			String hostname, String message, PromptingCertManager mgr) {
		ButtonType reject = new ButtonType(BUNDLE.getString("certificate.confirm.reject"));
		ButtonType accept = new ButtonType(BUNDLE.getString("certificate.confirm.accept"));
		Alert alert = createAlertWithOptOut(alertType, title, BUNDLE.getString("certificate.confirm.header"),
				MessageFormat.format(content, hostname, message),
				BUNDLE.getString("certificate.confirm.savePermanently"), param -> {
					if (param)
						mgr.save(key);
				}, accept, reject);
		alert.initModality(Modality.APPLICATION_MODAL);
		Stage stage = getStage();
		if (stage != null && stage.getOwner() != null)
			alert.initOwner(stage);

		alert.getButtonTypes().setAll(accept, reject);

		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == reject) {
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		var powerMonitor = PowerMonitor.get();
		powerMonitor.ifPresent(p -> {
			p.addListener(this);
			p.start();
		});

		this.primaryStage = primaryStage;
		detector = OsThemeDetector.getDetector();
		debugger.ifPresent(d->d.setup(this));

		// Setup the window
//		if (Platform.isSupported(ConditionalFeature.TRANSPARENT_WINDOW)) {
//			primaryStage.initStyle(StageStyle.TRANSPARENT);
//		} else {
//			primaryStage.initStyle(StageStyle.UNDECORATED);
//		}
		primaryStage.setTitle(BUNDLE.getString("title"));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon256x256.png")));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon128x128.png")));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon64x64.png")));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon48x48.png")));
		primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("logonbox-icon32x32.png")));

		Scene primaryScene = createWindows(primaryStage);

		// Finalise and show
		Configuration cfg = Configuration.getDefault();
		Main main = app;

		int x = cfg.xProperty().get();
		int y = cfg.yProperty().get();
		int h = cfg.hProperty().get();
		int w = cfg.wProperty().get();
		if (h == 0 && w == 0) {
			w = 457;
			h = 768;
		}
		if(w < 256) {
			w = 256;
		}
		else if(w > 1024) {
			w = 1024;
		}
		if(h < 256) {
			h = 256;
		}
		else if(h  > 1024) {
			h = 1024;
		}
		if (StringUtils.isNotBlank(main.getSize())) {
			String[] sizeParts = main.getSize().toLowerCase().split("x");
			w = Integer.parseInt(sizeParts[0]);
			h = Integer.parseInt(sizeParts[1]);
		}
		if (main.isNoMove()) {
			Rectangle2D screenBounds = Screen.getPrimary().getBounds();
			x = (int) (screenBounds.getMinX() + ((screenBounds.getWidth() - w) / 2));
			y = (int) (screenBounds.getMinY() + ((screenBounds.getHeight() - h) / 2));
		}
		primaryStage.setX(x);
		primaryStage.setY(y);
		primaryStage.setWidth(w);
		primaryStage.setHeight(h);
		primaryStage.setScene(primaryScene);
		primaryStage.show();
		primaryStage.xProperty().addListener((c, o, n) -> cfg.xProperty().set(n.intValue()));
		primaryStage.yProperty().addListener((c, o, n) -> cfg.yProperty().set(n.intValue()));
		primaryStage.widthProperty().addListener((c, o, n) -> cfg.wProperty().set(n.intValue()));
		primaryStage.heightProperty().addListener((c, o, n) -> cfg.hProperty().set(n.intValue()));
		primaryStage.setAlwaysOnTop(main.isAlwaysOnTop());
		primaryStage.setResizable(!main.isNoMove() && !main.isNoResize());

		/* Dark mode handling */
		cfg.darkModeProperty().addListener((c, o, n) -> {
			reapplyColors();
		});
		detector.registerListener(isDark -> {
			Platform.runLater(() -> {
				log.info("Dark mode is now " + isDark);
				reapplyColors();
				ui.reload();
			});
		});

		primaryStage.onCloseRequestProperty().set(we -> {
			if (!main.isNoClose())
				confirmExit();
			we.consume();
		});

		if (!main.isNoSystemTray()) {
			if (Taskbar.isTaskbarSupported() && SystemUtils.IS_OS_MAC_OSX) {
				tray = new AWTTaskbarTray(this);
			} else if (System.getProperty("logonbox.vpn.useAWTTray", "false").equals("true")
					|| (Util.isMacOs() && isHidpi()) || Util.isWindows())
				tray = new AWTTray(this);
			else
				tray = new DorkBoxTray(this);
		}
		ui.setAvailable();

		final SplashScreen splash = SplashScreen.getSplashScreen();
		if (splash != null) {
			splash.close();
		}

		keepInBounds(primaryStage);
		Screen.getScreens().addListener(new ListChangeListener<Screen>() {
			@Override
			public void onChanged(Change<? extends Screen> c) {
				keepInBounds(primaryStage);
			}
		});

		this.originalCookieHander = CookieHandler.getDefault();
		updateCookieHandlerState();

	}

	@Override
	public Styling styling() {
		return styling;
	}

	@Override
	public void wake() {
		log.info("Got Wake event.");
		ui.refresh();
	}

	protected void cleanUp() {
	    debugger.ifPresent(d -> d.close());

		if (tray != null) {
			try {
				tray.close();
			} catch (Exception e) {
			}
		}
	}

	protected CookieManager createCookieManager() {
		return new CookieManager(app.getCookieStore(), CookiePolicy.ACCEPT_ORIGINAL_SERVER);
	}

	protected Scene createWindows(Stage primaryStage) throws IOException {

		ui = new UI(this);
		
		// Open the actual scene
		var border = new BorderPane();
		border.setTop(titleBar);
		border.setCenter(ui);

		var scene = new Scene(border);
		var node = scene.getRoot();

		// For line store border
//		node.styleProperty().set("-fx-border-color: -fx-lbvpn-background;");

		applyColors(branding, node);

		if (isUndecoratedWindow()) {

			/* Anchor to stretch the content across the borderless window */
			AnchorPane anchor = new AnchorPane(node);
			AnchorPane.setBottomAnchor(node, 0d);
			AnchorPane.setLeftAnchor(node, 0d);
			AnchorPane.setTopAnchor(node, 0d);
			AnchorPane.setRightAnchor(node, 0d);

			BorderlessScene primaryScene = new BorderlessScene(primaryStage, StageStyle.TRANSPARENT, anchor, 1, 1);

			if (!app.isNoMove())
				primaryScene.setMoveControl(node);

			primaryScene.setDoubleClickMaximizeEnabled(false);
			primaryScene.setSnapEnabled(false);
			primaryScene.removeDefaultCSS();
			primaryScene.setResizable(!app.isNoMove() && !app.isNoResize());

			primaryScene.getRoot().setEffect(new DropShadow());
			((Region) primaryScene.getRoot()).setPadding(new Insets(10, 10, 10, 10));
			primaryScene.setFill(Color.TRANSPARENT);

			return primaryScene;
		} else {
			return scene;
		}
	}

	protected void keepInBounds(Stage primaryStage) {
		ObservableList<Screen> screens = Screen.getScreensForRectangle(primaryStage.getX(), primaryStage.getY(),
				primaryStage.getWidth(), primaryStage.getHeight());
		Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
		Rectangle2D bounds = screen.getVisualBounds();
		log.info(String.format("Moving into bounds %s from %f,%f", bounds, primaryStage.getX(), primaryStage.getY()));
		if (primaryStage.getX() < bounds.getMinX()) {
			primaryStage.setX(bounds.getMinX());
		} else if (primaryStage.getX() + primaryStage.getWidth() > bounds.getMaxX()) {
			primaryStage.setX(bounds.getMaxX() - primaryStage.getWidth());
		}
		if (primaryStage.getY() < bounds.getMinY()) {
			primaryStage.setY(bounds.getMinY());
		} else if (primaryStage.getY() + primaryStage.getHeight() > bounds.getMaxY()) {
			primaryStage.setY(bounds.getMaxY() - primaryStage.getHeight());
		}
	}

	protected void updateCookieHandlerState() {
		CookieHandler default1 = CookieHandler.getDefault();
		boolean isPersistJar = default1 instanceof CookieManager;
		boolean wantsPeristJar = Boolean.valueOf(System.getProperty("logonbox.vpn.saveCookies", "false"));
		if (isPersistJar != wantsPeristJar) {
			if (wantsPeristJar) {
				log.info("Using in custom cookie manager");
				CookieManager mgr = createCookieManager();
				CookieHandler.setDefault(mgr);
			} else {
				log.info("Using Webkit cookie manager");
				CookieHandler.setDefault(originalCookieHander);
			}
		}
	}

	void reapplyColors() {
		applyColors(branding, null);
	}

	private boolean isHidpi() {
		return Screen.getPrimary().getDpi() >= 300;
	}
}
