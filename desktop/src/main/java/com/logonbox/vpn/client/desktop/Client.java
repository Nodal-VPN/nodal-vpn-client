package com.logonbox.vpn.client.desktop;

import com.goxr3plus.fxborderlessscene.borderless.BorderlessScene;
import com.jthemedetecor.OsThemeDetector;
import com.logonbox.vpn.client.common.BrandingManager.BrandImage;
import com.logonbox.vpn.client.common.BrandingManager.ImageHandler;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.dbus.RemoteUI;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.dbus.client.DBusVpnManager;
import com.logonbox.vpn.client.gui.jfx.Configuration;
import com.logonbox.vpn.client.gui.jfx.Debugger;
import com.logonbox.vpn.client.gui.jfx.JfxAppContext;
import com.logonbox.vpn.client.gui.jfx.Navigator;
import com.logonbox.vpn.client.gui.jfx.Styling;
import com.logonbox.vpn.client.gui.jfx.UI;
import com.logonbox.vpn.client.gui.jfx.UIContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.SplashScreen;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
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
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.Resource;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Bundle
@Resource(siblings = true)
@Reflectable
@TypeReflect(constructors = true, classes = true)
public class Client extends Application implements UIContext<VpnConnection>, RemoteUI {

	static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true").equals("true");

	public static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());

	static Logger log = LoggerFactory.getLogger(Client.class);

	public static Alert createAlertWithOptOut(AlertType type, String title, String headerText, String message,
			String optOutMessage, Consumer<Boolean> optOutAction, ButtonType... buttonTypes) {
		var alert = new Alert(type);
		alert.getDialogPane().applyCss();

		var graphic = alert.getDialogPane().getGraphic();
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
	private OsThemeDetector detector;
	private ExecutorService opQueue = Executors.newSingleThreadExecutor();
	private Stage primaryStage;
	private boolean waitingForExitChoice;
	private UI<VpnConnection> ui;
	private Main app;
	private Styling styling;
	private TitleBar titleBar;

    private Scene primaryScene;

	public Client() {
		app = Main.getInstance().uiContext(this);
	}

	@Override
    public void reapplyBranding() {
	    var node = primaryScene.getRoot();
		var branding = ui.getBrandingManager().branding().orElse(null);

		var ss = node.getStylesheets();

		/* No branding, remove the custom styles if there are any */
		ss.clear();

		/* Create new custom styles */
		styling.apply(branding == null ? null : branding.branding());
		var tmpFile = styling.getCustomJavaFXCSSFile();
		if (log.isDebugEnabled())
			log.debug(String.format("Using custom JavaFX stylesheet %s", tmpFile));
		var uri = Styling.toUri(tmpFile).toExternalForm();
		ss.add(0, uri);
		var css = Client.class.getResource(Client.class.getSimpleName() + ".css").toExternalForm();
		ss.add(css);

        String defaultLogo = UI.class.getResource("logonbox-titlebar-logo.png").toExternalForm();
        if ((branding == null || branding.logo().isEmpty())
                && !defaultLogo.equals(navigator().getImage().getUrl())) {
            navigator().setImage(new Image(defaultLogo, true));
        } else if (branding != null && branding.logo().isPresent() && !defaultLogo.equals(branding.logo().get().toUri().toString())) {
            navigator().setImage(new Image(branding.logo().get().toUri().toString(), true));
        }
    }
    
	@Override
	public void back() {
		ui.back();
	}

	public void clearLoadQueue() {
		opQueue.shutdownNow();
		opQueue = Executors.newSingleThreadExecutor();
	}

	@Override
    public void confirmExit() {
		var active = 0;
		try {
			active = app.getVpnManager().getVpnOrFail().getActiveButNonPersistentConnections();
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
                        app.getVpnManager().getVpnOrFail().disconnectAll();
                    }

                    if (result.get() == disconnect || result.get() == stayConnected) {
                        try {
                            app.getVpnManager().confirmExit();
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
	}

    @Override
    public String getObjectPath() {
        return RemoteUI.OBJECT_PATH;
    }

	@Override
	public Optional<Debugger> debugger() {
		return debugger;
	}

	@Override
	public void exitApp() {
		app.shutdown(false);
		opQueue.shutdown();
		Platform.exit();
	}

	@Override
	public JfxAppContext<VpnConnection> getAppContext() {
		return app;
	}

	@Override
	public ExecutorService getOpQueue() {
		return opQueue;
	}

	public Stage getStage() {
		return primaryStage;
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
        ((DBusVpnManager)app.getVpnManager()).getAltBus().requestBusName(RemoteUI.BUS_NAME);
		((DBusVpnManager)app.getVpnManager()).getAltBus().exportObject(RemoteUI.OBJECT_PATH, this);

		Platform.setImplicitExit(false);
	}

	@Override
	public boolean isAllowBranding() {
		return allowBranding;
	}

	@Override
	public boolean isContextMenuAllowed() {
		return debugger().isPresent();
	}

	@Override
    public boolean isDarkMode() {
		var mode = Configuration.getDefault().darkModeProperty().get();
		if (mode.equals(Configuration.DARK_MODE_AUTO))
			return detector.isDark();
		else if (mode.equals(Configuration.DARK_MODE_ALWAYS))
			return true;
		else
			return false;
	}

	@Override
    public boolean isMinimizeAllowed() {
		return true;
	}

	@Override
	public boolean isTrayConfigurable() {
		return true;
	}

	@Override
    public boolean isUndecoratedWindow() {
		return System.getProperty("logonbox.vpn.undecoratedWindow", "true").equals("true");
	}

	public boolean isWaitingForExitChoice() {
		return waitingForExitChoice;
	}

	@Override
    public void maybeExit() {
			confirmExit();
	}

	@Override
	public void minimize() {
		getStage().setIconified(true);
	}

	@Override
	public Navigator navigator() {
		return titleBar;
	}

	@Override
    public boolean isRemote() {
        return true;
    }

    @Override
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

	@Override
    public void options() {
		open();
		Platform.runLater(() -> ui.options());
	}

	@Override
    public boolean promptForCertificate(AlertType alertType, String title, String content, String key,
			String hostname, String message, PromptingCertManager mgr) {
		var reject = new ButtonType(BUNDLE.getString("certificate.confirm.reject"));
		var accept = new ButtonType(BUNDLE.getString("certificate.confirm.accept"));
		var alert = createAlertWithOptOut(alertType, title, BUNDLE.getString("certificate.confirm.header"),
				MessageFormat.format(content, hostname, message),
				BUNDLE.getString("certificate.confirm.savePermanently"), param -> {
					if (param)
						mgr.save(key);
				}, accept, reject);
		alert.initModality(Modality.APPLICATION_MODAL);
		var stage = getStage();
		if (stage != null && stage.getOwner() != null)
			alert.initOwner(stage);

		alert.getButtonTypes().setAll(accept, reject);

		var result = alert.showAndWait();
		if (result.get() == reject) {
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
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

		primaryScene = createWindows(primaryStage);
        reapplyBranding();

		// Finalise and show
		var cfg = Configuration.getDefault();
		var main = app;

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
		if (Utils.isNotBlank(main.getSize())) {
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
			reapplyBranding();
		});
		detector.registerListener(isDark -> {
			Platform.runLater(() -> {
				log.info("Dark mode is now " + isDark);
	            reapplyBranding();
				ui.reload();
			});
		});

		primaryStage.onCloseRequestProperty().set(we -> {
			if (!main.isNoClose())
				confirmExit();
			we.consume();
		});

		ui.setAvailable();

		var splash = SplashScreen.getSplashScreen();
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

	protected void cleanUp() {
	    debugger.ifPresent(d -> d.close());
	}

	protected CookieManager createCookieManager() {
		return new CookieManager(app.getCookieStore(), CookiePolicy.ACCEPT_ORIGINAL_SERVER);
	}

	protected Scene createWindows(Stage primaryStage) throws IOException {

		ui = new UI<>(this);
		ui.getBrandingManager().addBrandingChangeListener((bd) -> {
		    reapplyBranding();
		});

		// Open the actual scene
		var border = new BorderPane();
		border.setTop(titleBar);
		border.setCenter(ui);

		var scene = new Scene(border);
		var node = scene.getRoot();

		// For line store border
//		node.styleProperty().set("-fx-border-color: -fx-lbvpn-background;");

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

		var screens = Screen.getScreensForRectangle(primaryStage.getX(), primaryStage.getY(),
				primaryStage.getWidth(), primaryStage.getHeight());
		var screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
		var bounds = screen.getVisualBounds();

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
		var default1 = CookieHandler.getDefault();
		var isPersistJar = default1 instanceof CookieManager;
		var wantsPeristJar = Boolean.valueOf(System.getProperty("logonbox.vpn.saveCookies", "false"));

		if (isPersistJar != wantsPeristJar) {
			if (wantsPeristJar) {
				log.info("Using in custom cookie manager");
				var mgr = createCookieManager();
				CookieHandler.setDefault(mgr);
			} else {
				log.info("Using Webkit cookie manager");
				CookieHandler.setDefault(originalCookieHander);
			}
		}
	}

	private boolean isHidpi() {
		return Screen.getPrimary().getDpi() >= 300;
	}

    @Override
    public ImageHandler getImageHandler() {
        return new ImageHandler() {

            @Override
            public BrandImage create(int width, int height, String color) {
                BufferedImage bim = null;
                bim = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                var graphics = (java.awt.Graphics2D) bim.getGraphics();
                graphics.setColor(java.awt.Color.decode(color));
                graphics.fillRect(0, 0, width, height);
                return new AWTBrandImage(bim);
            }

            @Override
            public void draw(BrandImage bim, Path logoFile) {
                var img = ((AWTBrandImage)bim).img;
                var graphics = (java.awt.Graphics2D) img.getGraphics();
                log.info(String.format("Drawing logo on splash"));
                try {
                    var logoImage = ImageIO.read(logoFile.toFile());
                    if (logoImage == null)
                        throw new IOException(String.format("Failed to load image from %s", logoFile));
                    graphics.drawImage(logoImage, (img.getWidth() - logoImage.getWidth()) / 2,
                            (img.getHeight() - logoImage.getHeight()) / 2, null);
                }
                catch(IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            }

            @Override
            public void write(BrandImage bim, Path splashFile) throws IOException {
                ImageIO.write(((AWTBrandImage)bim).img, "png", splashFile.toFile());
                log.info(String.format("Custom splash written to %s", splashFile));
            }
        };
    }
    
    private final static class AWTBrandImage implements BrandImage {
        private BufferedImage img;
        
        AWTBrandImage(BufferedImage img) {
            this.img = img;
        }
    }
}
