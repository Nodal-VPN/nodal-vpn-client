package com.logonbox.vpn.client.gui.jfx;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.goxr3plus.fxborderlessscene.borderless.BorderlessScene;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.api.BrandingInfo;
import com.logonbox.vpn.common.client.dbus.RemoteUI;

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
import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class Client extends Application implements RemoteUI {

	static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true").equals("true");

	static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());

	static UUID localWebServerCookie = UUID.randomUUID();

	static final String LOCBCOOKIE = "LOCBCKIE";
	static Logger log = LoggerFactory.getLogger(Client.class);

	private CookieHandler originalCookieHander;
	private static Client instance;

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

	static URL toUri(File tmpFile) {
		try {
			return tmpFile.toURI().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	private Branding branding;
//	private OsThemeDetector detector;
	private ExecutorService opQueue = Executors.newSingleThreadExecutor();
	private Stage primaryStage;
	private boolean waitingForExitChoice;

	private UI ui;

	private ServiceLoader<ClientExtension> extensions;

	public void clearLoadQueue() {
		opQueue.shutdownNow();
		opQueue = Executors.newSingleThreadExecutor();
	}

	@Override
	public void confirmExit() {
		Platform.runLater(() -> {
			int active = 0;
			try {
				active = getDBus().getVPN().getActiveButNonPersistentConnections();
				log.info("{} non-persistent connections", active);
			} catch (Exception e) {
				exitApp();
			}

			if (active > 0) {
				var alert = new Alert(AlertType.CONFIRMATION);
				alert.initModality(Modality.APPLICATION_MODAL);
				alert.initOwner(getStage());
				alert.setTitle(BUNDLE.getString("exit.confirm.title"));
				alert.setHeaderText(BUNDLE.getString("exit.confirm.header"));
				alert.setContentText(BUNDLE.getString("exit.confirm.content"));

				var disconnect = new ButtonType(BUNDLE.getString("exit.confirm.disconnect"));
				var stayConnected = new ButtonType(BUNDLE.getString("exit.confirm.stayConnected"));
				var cancel = new ButtonType(BUNDLE.getString("exit.confirm.cancel"), ButtonData.CANCEL_CLOSE);

				alert.getButtonTypes().setAll(disconnect, stayConnected, cancel);
				waitingForExitChoice = true;
				try {
					var result = alert.showAndWait();
					opQueue.execute(() -> {
						if (result.get() == disconnect) {
							getDBus().getVPN().disconnectAll();
						}

						if (result.get() == disconnect || result.get() == stayConnected) {
							try {
								getDBus().getBus().sendMessage(new ConfirmedExit(RemoteUI.OBJECT_PATH));
								Thread.sleep(1000);
							} catch (Exception e) {
							}
							exitApp();
						}
					});
				} finally {
					waitingForExitChoice = false;
				}
			} else {
				exitApp();
			}
		});
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
		extensions = ServiceLoader.load(ClientExtension.class);
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				cleanUp();
			}
		});

		Platform.setImplicitExit(false);
		instance = this;
	}

	public boolean isMinimizeAllowed() {
		return true;
	}

	public boolean isTrayConfigurable() {
		return true;
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

	protected CookieManager createCookieManager() {
		return new CookieManager(Main.getInstance().getCookieStore(), CookiePolicy.ACCEPT_ORIGINAL_SERVER);
	}

	public boolean isWaitingForExitChoice() {
		return waitingForExitChoice;
	}

	public void maybeExit() {
		confirmExit();
	}

	@Override
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
		URL resource = UI.class.getResource("UI.fxml");
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

	@Override
	public void options() {
		open();
		Platform.runLater(() -> ui.options());
	}

	@Override
	public void start(Stage primaryStage) throws Exception {

		this.primaryStage = primaryStage;
//		detector = OsThemeDetector.getDetector();
		
		for(var ext : extensions)
			ext.start(this);

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
		if (w < 256) {
			w = 256;
		} else if (w > 1024) {
			w = 1024;
		}
		if (h < 256) {
			h = 256;
		} else if (h > 1024) {
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
//		detector.registerListener(isDark -> {
//			Platform.runLater(() -> {
//				log.info("Dark mode is now " + isDark);
//				reapplyColors();
//				ui.reload();
//			});
//		});

		primaryStage.onCloseRequestProperty().set(we -> {
			if (!main.isNoClose())
				confirmExit();
			we.consume();
		});

		ui.setAvailable();

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

	protected void keepInBounds(Stage primaryStage) {
		ObservableList<Screen> screens = Screen.getScreensForRectangle(primaryStage.getX(), primaryStage.getY(),
				primaryStage.getWidth(), primaryStage.getHeight());
		Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
		Rectangle2D bounds = screen.getVisualBounds();
		log.info(String.format("Moving into bounds %s from %f,%f", bounds, primaryStage.getX(), primaryStage.getY()));
		boolean moved = false;
		if (primaryStage.getX() < bounds.getMinX()) {
			primaryStage.setX(bounds.getMinX());
			moved = true;
		} else if (primaryStage.getX() + primaryStage.getWidth() > bounds.getMaxX()) {
			primaryStage.setX(bounds.getMaxX() - primaryStage.getWidth());
			moved = true;
		}
		if (primaryStage.getY() < bounds.getMinY()) {
			primaryStage.setY(bounds.getMinY());
			moved = true;
		} else if (primaryStage.getY() + primaryStage.getHeight() > bounds.getMaxY()) {
			primaryStage.setY(bounds.getMaxY() - primaryStage.getHeight());
			moved = true;
		}
		if(moved)
			log.info(String.format("Moved into bounds %s from %f,%f", bounds, primaryStage.getX(), primaryStage.getY()));
		else
			log.info("No movement required");
	}

	protected Scene createWindows(Stage primaryStage) throws IOException {

		// Open the actual scene
		ui = openScene();
		Scene scene = ui.getScene();
		Parent node = scene.getRoot();

		// For line store border
//		node.styleProperty().set("-fx-border-color: -fx-lbvpn-background;");

		applyColors(branding, node);

		if (!Main.getInstance().isWindowDecorations()) {

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

	@Override
	public String getObjectPath() {
		return RemoteUI.OBJECT_PATH;
	}

	protected void cleanUp() {
		for(var ext : extensions)
			ext.cleanUp();
	}

	@Override
	public void exitApp() {
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
		var uri = toUri(tmpFile).toExternalForm();
		ss.add(0, uri);
		var css = Client.class.getResource(Client.class.getSimpleName() + ".css").toExternalForm();
		ss.add(css);

	}

	public File getTempDir() {
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			return new File(System.getProperty("java.io.tmpdir"));
		else
			return new File(System.getProperty("hypersocket.bootstrap.distDir")).getParentFile();
	}
	
	public ServiceLoader<ClientExtension> getExtensions() {
		return extensions;
	}

	File getCustomJavaFXCSSFile() {
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			return new File(getTempDir(), System.getProperty("user.name") + "-lbvpn-jfx.css");
		else
			return new File(getTempDir(), "lbvpn-jfx.css");
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

		if (Colors.contrast(baseColor, backgroundColour) < 3) {
			/* Brightness is similar, use foreground as basic of link color */
			var b3 = foregroundColour.getBrightness();
			if (b3 > 0.5)
				linkColor = foregroundColour.deriveColor(0, 0, 0.75, 1.0);
			else
				linkColor = foregroundColour.deriveColor(0, 0, 1.25, 1.0);
		} else {
			/* Brightness is dissimilar, use foreground as basic of link color */
			linkColor = backgroundColour;
		}

		bui.append("* {\n");

		bui.append("-fx-lbvpn-background: ");
		bui.append(Colors.toHex(backgroundColour));
		bui.append(";\n");

		bui.append("-fx-lbvpn-foreground: ");
		bui.append(Colors.toHex(foregroundColour));
		bui.append(";\n");

		bui.append("-fx-lbvpn-base: ");
		bui.append(Colors.toHex(getBase()));
		bui.append(";\n");

		bui.append("-fx-lbvpn-base-inverse: ");
		bui.append(Colors.toHex(getBaseInverse()));
		bui.append(";\n");

		bui.append("-fx-lbvpn-link: ");
		bui.append(Colors.toHex(linkColor));
		bui.append(";\n");

//
		// Highlight
		if (backgroundColour.getSaturation() == 0) {
			// Greyscale, so just use HS blue
			bui.append("-fx-lbvpn-accent: 000033;\n");
			bui.append("-fx-lbvpn-accent2: 0e0041;\n");
		} else {
			// A colour, so choose the next adjacent colour in the HSB colour
			// wheel (45 degrees)
			bui.append("-fx-lbvpn-accent: " + Colors.toHex(backgroundColour.deriveColor(45f, 1f, 1f, 1f)) + ";\n");
			bui.append("-fx-lbvpn-accent: " + Colors.toHex(backgroundColour.deriveColor(-45f, 1f, 1f, 1f)) + ";\n");
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
			//return detector.isDark();
			return true;
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

		if (Colors.contrast(baseColor, bgColor) < 3) {
			/* Brightness is similar, use foreground as basic of link color */
			var b3 = fgColor.getBrightness();
			if (b3 > 0.5)
				linkColor = fgColor.deriveColor(0, 0, 0.75, 1.0);
			else
				linkColor = fgColor.deriveColor(0, 0, 1.25, 1.0);
		} else {
			/* Brightness is dissimilar, use foreground as basic of link color */
			linkColor = bgColor;
		}

		String accent1Str = Colors.toHex(bgColor.deriveColor(0, 1, 0.85, 1));
		String accent2Str = Colors.toHex(bgColor.deriveColor(0, 1, 1.15, 1));
		String baseStr = Colors.toHex(baseColor);
		String baseInverseStr = Colors.toHex(getBaseInverse());
		String linkStr = Colors.toHex(linkColor);
		String baseInverseRgbStr = Colors.toRgba(getBaseInverse(), 0.05f);
		try (PrintWriter output = new PrintWriter(new FileWriter(tmpFile))) {
			try (BufferedReader input = new BufferedReader(
					new InputStreamReader(UI.class.getResource("local.css").openStream()))) {
				String line;
				while ((line = input.readLine()) != null) {
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
}
