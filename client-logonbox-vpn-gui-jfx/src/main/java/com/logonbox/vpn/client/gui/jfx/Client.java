package com.logonbox.vpn.client.gui.jfx;

import java.awt.SplashScreen;
import java.awt.Taskbar;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.PropertyConfigurator;
import org.freedesktop.dbus.utils.Util;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.goxr3plus.fxborderlessscene.borderless.BorderlessScene;
import com.jthemedetecor.OsThemeDetector;
import com.logonbox.vpn.client.gui.jfx.PowerMonitor.Listener;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.CustomCookieStore;
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.api.BrandingInfo;
import com.vladsch.boxed.json.BoxedJsObject;
import com.vladsch.boxed.json.BoxedJson;
import com.vladsch.javafx.webview.debugger.JfxScriptStateProvider;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
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
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Client extends Application implements JfxScriptStateProvider, Listener {

	static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true").equals("true");

	static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());

	static UUID localWebServerCookie = UUID.randomUUID();

	static final String LOCBCOOKIE = "LOCBCKIE";
	static Logger log = LoggerFactory.getLogger(Client.class);

	private CookieHandler originalCookieHander;
	private static Client instance;

	static private BoxedJsObject jsstate = BoxedJson.of(); // start with empty state

	@NotNull
	@Override
	public BoxedJsObject getState() {
		return jsstate;
	}

	@Override
	public void setState(@NotNull final BoxedJsObject state) {
		jsstate = state;
	}

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

	public static Client get() {
		return instance;
	}

	static String toHex(Color color) {
		return toHex(color, -1);
	}

	static String toHex(Color color, boolean opacity) {
		return toHex(color, opacity ? color.getOpacity() : -1);
	}

	static String toHex(Color color, double opacity) {
		if (opacity > -1)
			return String.format("#%02x%02x%02x%02x", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
					(int) (color.getBlue() * 255), (int) (opacity * 255));
		else
			return String.format("#%02x%02x%02x", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
					(int) (color.getBlue() * 255));
	}

	static String toRgb(Color color) {
		return toRgba(color, -1);
	}

	static String toRgba(Color color, boolean opacity) {
		return toRgba(color, opacity ? color.getOpacity() : -1);
	}
	
	static String toRgba(Color color, double opacity) {
		if (opacity > -1)
			return String.format("rgba(%3f,%3f,%3f,%3f)", color.getRed(), color.getGreen(),
					color.getBlue(), opacity);
		else
			return String.format("rgba(%3f,%3f,%3f)", color.getRed(), color.getGreen(),
					color.getBlue());
	}

	static URL toUri(File tmpFile) {
		try {
			return tmpFile.toURI().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	private Branding branding;

	private OsThemeDetector detector;

	private ExecutorService opQueue = Executors.newSingleThreadExecutor();

	private Stage primaryStage;

	private Tray tray;

	private boolean waitingForExitChoice;

	private UI ui;

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

	public AbstractDBusClient getDBus() {
		return Main.getInstance();
	}

	public ExecutorService getOpQueue() {
		return opQueue;
	}

	public Stage getStage() {
		return primaryStage;
	}

	@Override
	public void init() throws Exception {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				cleanUp();
			}
		});

		Platform.setImplicitExit(false);
		instance = this;
		PropertyConfigurator.configureAndWatch(
				System.getProperty("hypersocket.logConfiguration", "conf" + File.separator + "log4j.properties"));
	}

	public boolean isMinimizeAllowed() {
		return tray == null || tray instanceof AWTTaskbarTray;
	}

	public boolean isTrayConfigurable() {
		return tray != null && tray.isConfigurable();
	}

	public Tray getTray() {
		return tray;
	}

	protected void updateCookieHandlerState() {
		CookieHandler default1 = CookieHandler.getDefault();
		boolean isPersistJar = default1 instanceof CookieManager;
//		boolean wantsPeristJar = Configuration.getDefault().saveCookiesProperty().get();
		boolean wantsPeristJar = false;
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

	protected CookieManager createCookieManager() {
		CookieStore store = new CustomCookieStore();
		CookieManager mgr = new CookieManager(store, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
		return mgr;
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

	public void open() {
		log.info("Open request");
		Platform.runLater(() -> {
			if (primaryStage.isIconified())
				primaryStage.setIconified(false);
			primaryStage.show();
			primaryStage.toFront();
		});
	}

	public UI openScene() throws IOException {
		URL resource = UI.class
				.getResource("UI.fxml");
		FXMLLoader loader = new FXMLLoader();
		ResourceBundle resources = ResourceBundle.getBundle(UI.class.getName());
		loader.setResources(resources);
		Parent root = loader.load(resource.openStream());
		UI controllerInst = (UI) loader.getController();
		if (controllerInst == null) {
			throw new IOException("Controller not found. Check controller in FXML");
		}
		controllerInst.initialize(resource, resources);
		Scene scene = new Scene(root);
		controllerInst.configure(scene, this);
		return controllerInst;
	}

	public void options() {
		open();
		Platform.runLater(() -> ui.options());
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		PowerMonitor powerMonitor = PowerMonitor.get();
		powerMonitor.addListener(this);
		powerMonitor.start();
		
		this.primaryStage = primaryStage;
		detector = OsThemeDetector.getDetector();
		
        try {
            File stateFile = new File(getTempDir(), "debug.json");
            if (stateFile.exists()) {
                FileReader stateReader = new FileReader(stateFile);
                jsstate = BoxedJson.boxedFrom(stateReader);
                stateReader.close();
            }
        } catch (IOException e) {
        }

        if (!jsstate.isValid()) {
            jsstate = BoxedJson.of();
        }

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
		Main main = Main.getInstance();

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
		Configuration.getDefault().saveCookiesProperty().addListener((e) -> updateCookieHandlerState());

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

	protected Scene createWindows(Stage primaryStage) throws IOException {

		// Open the actual scene
		ui = openScene();
		Scene scene = ui.getScene();
		Parent node = scene.getRoot();

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

			if (!Main.getInstance().isNoMove())
				primaryScene.setMoveControl(node);

			primaryScene.setDoubleClickMaximizeEnabled(false);
			primaryScene.setSnapEnabled(false);
			primaryScene.removeDefaultCSS();
			primaryScene.setResizable(!Main.getInstance().isNoMove() && !Main.getInstance().isNoResize());

			primaryScene.getRoot().setEffect(new DropShadow());
			((Region) primaryScene.getRoot()).setPadding(new Insets(10, 10, 10, 10));
			primaryScene.setFill(Color.TRANSPARENT);

			return primaryScene;
		} else {
			return scene;
		}
	}

	public boolean isUndecoratedWindow() {
		return System.getProperty("logonbox.vpn.undecoratedWindow", "true").equals("true");
	}

	protected void cleanUp() {
        File stateFile = new File(getTempDir(), "debug.json");
        if (jsstate.isEmpty()) {
            stateFile.delete();
        } else {
            // save state for next run
            try {
                FileWriter stateWriter = new FileWriter(stateFile);
                stateWriter.write(jsstate.toString());
                stateWriter.flush();
                stateWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
		if (tray != null) {
			try {
				tray.close();
			} catch (Exception e) {
			}
		}
	}

	protected void exitApp() {
		Main.getInstance().exit();
		opQueue.shutdown();
		Platform.exit();
	}

	protected Color getBase() {
		if (isDarkMode()) {
			if (SystemUtils.IS_OS_LINUX)
				return Color.valueOf("#1c1f22");
			else if (SystemUtils.IS_OS_MAC_OSX)
				return Color.valueOf("#231f25");
			else
				return Color.valueOf("#202020");
		} else
			return Color.WHITE;
	}

	protected Color getBaseInverse() {
		if (isDarkMode())
			return Color.WHITE;
		else
			return Color.BLACK;
	}

	protected boolean promptForCertificate(AlertType alertType, String title, String content, String key,
			String hostname, String message, Preferences preference) {
		ButtonType reject = new ButtonType(BUNDLE.getString("certificate.confirm.reject"));
		ButtonType accept = new ButtonType(BUNDLE.getString("certificate.confirm.accept"));
		Alert alert = createAlertWithOptOut(alertType, title, BUNDLE.getString("certificate.confirm.header"),
				MessageFormat.format(content, hostname, message),
				BUNDLE.getString("certificate.confirm.savePermanently"), param -> {
					if (param)
						preference.putBoolean(key, true);
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

	void applyColors(Branding branding, Parent node) {
		if (node == null && ui != null)
			node = ui.getScene().getRoot();

		this.branding = branding;

		ObservableList<String> ss = node.getStylesheets();

		/* No branding, remove the custom styles if there are any */
		ss.clear();

		/* Create new custom local web styles */
		writeLocalWebCSS(branding);

		/* Create new JavaFX custom styles */
		writeJavaFXCSS(branding);
		File tmpFile = getCustomJavaFXCSSFile();
		if (log.isDebugEnabled())
			log.debug(String.format("Using custom JavaFX stylesheet %s", tmpFile));
		ss.add(0, toUri(tmpFile).toExternalForm());

		ss.add(Client.class.getResource(Client.class.getSimpleName() + ".css").toExternalForm());

	}
	
	File getTempDir() {
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			return new File(System.getProperty("java.io.tmpdir"));
		else
			return new File(System.getProperty("hypersocket.bootstrap.distDir")).getParentFile();
	}

	File getCustomJavaFXCSSFile() {
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			return new File(getTempDir(),
					System.getProperty("user.name") + "-lbvpn-jfx.css");
		else
			return new File(getTempDir(),
					"lbvpn-jfx.css");
	}

	String getCustomJavaFXCSSResource(Branding branding) {
		StringBuilder bui = new StringBuilder();

		// Get the base colour. All other colours are derived from this
		Color backgroundColour = Color
				.valueOf(branding == null ? BrandingInfo.DEFAULT_BACKGROUND : branding.getResource().getBackground());
		Color foregroundColour = Color
				.valueOf(branding == null ? BrandingInfo.DEFAULT_FOREGROUND : branding.getResource().getForeground());

		if (backgroundColour.getOpacity() == 0) {
			// Prevent total opacity, as mouse events won't be received
			backgroundColour = new Color(backgroundColour.getRed(), backgroundColour.getGreen(),
					backgroundColour.getBlue(), 1f / 255f);
		}
		
		Color baseColor = getBase();
		Color linkColor;

		if(contrast(baseColor, backgroundColour) < 3) {
			/* Brightness is similar, use foreground as basic of link color */
			var b3 = foregroundColour.getBrightness();
			if(b3 > 0.5)
				linkColor = foregroundColour.deriveColor(0, 0, 0.75, 1.0);
			else
				linkColor = foregroundColour.deriveColor(0, 0, 1.25, 1.0);
		}
		else {
			/* Brightness is dissimilar, use foreground as basic of link color */
			linkColor = backgroundColour;
		}

		bui.append("* {\n");

		bui.append("-fx-lbvpn-background: ");
		bui.append(toHex(backgroundColour));
		bui.append(";\n");

		bui.append("-fx-lbvpn-foreground: ");
		bui.append(toHex(foregroundColour));
		bui.append(";\n");

		bui.append("-fx-lbvpn-base: ");
		bui.append(toHex(getBase()));
		bui.append(";\n");

		bui.append("-fx-lbvpn-base-inverse: ");
		bui.append(toHex(getBaseInverse()));
		bui.append(";\n");

		bui.append("-fx-lbvpn-link: ");
		bui.append(toHex(linkColor));
		bui.append(";\n");

//
		// Highlight
		if (backgroundColour.getSaturation() == 0) {
			// Greyscale, so just use HS blue
			bui.append("-fx-lbvpn-accent: 1e0c51;\n");
			bui.append("-fx-lbvpn-accent2: 0e0041;\n");
		} else {
			// A colour, so choose the next adjacent colour in the HSB colour
			// wheel (45 degrees)
			bui.append("-fx-lbvpn-accent: " + toHex(backgroundColour.deriveColor(45f, 1f, 1f, 1f)) + ";\n");
			bui.append("-fx-lbvpn-accent: " + toHex(backgroundColour.deriveColor(-45f, 1f, 1f, 1f)) + ";\n");
		}

		// End
		bui.append("}\n");

		return bui.toString();

	}

	File getCustomLocalWebCSSFile() {
		File tmpFile;
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			tmpFile = new File(getTempDir(), System.getProperty("user.name") + "-lbvpn-web.css");
		else
			tmpFile = new File(getTempDir(), "lbvpn-web.css");
		return tmpFile;
	}

	void reapplyColors() {
		applyColors(branding, null);
	}

	void writeJavaFXCSS(Branding branding) {
		try {
			File tmpFile = getCustomJavaFXCSSFile();
			String url = toUri(tmpFile).toExternalForm();
			if (log.isDebugEnabled())
				log.debug(String.format("Writing JavafX style sheet to %s", url));
			PrintWriter pw = new PrintWriter(new FileOutputStream(tmpFile));
			try {
				pw.println(getCustomJavaFXCSSResource(branding));
			} finally {
				pw.close();
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not create custom CSS resource.");
		}
	}

	boolean isDarkMode() {
		String mode = Configuration.getDefault().darkModeProperty().get();
		if (mode.equals(Configuration.DARK_MODE_AUTO))
			return detector.isDark();
		else if (mode.equals(Configuration.DARK_MODE_ALWAYS))
			return true;
		else
			return false;
	}

	void writeLocalWebCSS(Branding branding) {
		File tmpFile = getCustomLocalWebCSSFile();
		tmpFile.getParentFile().mkdirs();
		String url = toUri(tmpFile).toExternalForm();
		if (log.isDebugEnabled())
			log.debug(String.format("Writing local web style sheet to %s", url));
		String bgStr = branding == null ? BrandingInfo.DEFAULT_BACKGROUND : branding.getResource().getBackground();
		String fgStr = branding == null ? BrandingInfo.DEFAULT_FOREGROUND : branding.getResource().getForeground();
		Color fgColor = Color.valueOf(fgStr);
		Color bgColor = Color.valueOf(bgStr);
		Color baseColor = getBase();
		Color linkColor;

		if(contrast(baseColor, bgColor) < 3) {
			/* Brightness is similar, use foreground as basic of link color */
			var b3 = fgColor.getBrightness();
			if(b3 > 0.5)
				linkColor = fgColor.deriveColor(0, 0, 0.75, 1.0);
			else
				linkColor = fgColor.deriveColor(0, 0, 1.25, 1.0);
		}
		else {
			/* Brightness is dissimilar, use foreground as basic of link color */
			linkColor = bgColor;
		}
		
		String accent1Str = toHex(bgColor.deriveColor(0, 1, 0.85, 1));
		String accent2Str = toHex(bgColor.deriveColor(0, 1, 1.15, 1));
		String baseStr = toHex(baseColor);
		String baseInverseStr = toHex(getBaseInverse());
		String linkStr = toHex(linkColor);
		String baseInverseRgbStr = toRgba(getBaseInverse(), 0.05f);
		try (PrintWriter output = new PrintWriter(new FileWriter(tmpFile))) {
			try (BufferedReader input = new BufferedReader(new InputStreamReader(UI.class.getResource("local.css").openStream()))) {
				String line;
				while(( line = input.readLine()) != null) {
					line = line.replace("${lbvpnBackground}", bgStr);
					line = line.replace("${lbvpnForeground}", fgStr);
					line = line.replace("${lbvpnAccent}", accent1Str);
					line = line.replace("${lbvpnAccent2}", accent2Str);
					line = line.replace("${lbvpnLink}", linkStr);
					line = line.replace("${lbvpnBase}", baseStr);
					line = line.replace("${lbvpnBaseInverse}", baseInverseStr);
					line = line.replace("${lbvpnBaseInverseRgb}", baseInverseRgbStr);
					output.println(line);
				}
			}
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed to load local style sheet template.", ioe);
		}
	}

	private boolean isHidpi() {
		return Screen.getPrimary().getDpi() >= 300;
	}

	public boolean isChromeDebug() {
		return Boolean.getBoolean("logonbox.vpn.chromeDebug");
	}

	@Override
	public void wake() {
		// TODO Auto-generated method stub
		log.info("Got Wake event.");
		ui.refresh();
	}

	static double lum(Color c) {
		List<Double> a = Arrays.asList(c.getRed(), c.getGreen(), c.getBlue()).stream()
				.map(v -> v <= 0.03925 ? v / 12.92f : Math.pow((v + 0.055f) / 1.055f, 2.4f)).collect(Collectors.toList());
		return a.get(0) * 0.2126 + a.get(1) * 0.7152 + a.get(2) * 0.0722;
	}
	
	static double contrast(Color c1, Color c2) {
		var brightest = Math.max(lum(c1), lum(c2));
	    var darkest = Math.min(lum(c1), lum(c2));
	    return (brightest + 0.05f)
	         / (darkest + 0.05f);
	}
}
