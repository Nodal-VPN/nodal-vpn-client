package com.logonbox.vpn.client.mobile;

import com.gluonhq.attach.display.DisplayService;
import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.mvc.View;
import com.logonbox.vpn.client.common.AppContext;
import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.lbapi.Branding;
import com.logonbox.vpn.client.embedded.EmbeddedVpnConnection;
import com.logonbox.vpn.client.gui.jfx.Configuration;
import com.logonbox.vpn.client.gui.jfx.Debugger;
import com.logonbox.vpn.client.gui.jfx.JfxAppContext;
import com.logonbox.vpn.client.gui.jfx.Navigator;
import com.logonbox.vpn.client.gui.jfx.Styling;
import com.logonbox.vpn.client.gui.jfx.UI;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.sshtools.twoslices.ToasterFactory;
import com.sshtools.twoslices.ToasterSettings.SystemTrayIconMode;
import com.sshtools.twoslices.impl.SysOutToaster;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Dimension2D;
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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Modality;

public class Client extends MobileApplication implements UIContext<EmbeddedVpnConnection>, Navigator {

	static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true").equals("true");

	public static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());
	static Logger log = LoggerFactory.getLogger(Client.class);

	private CookieHandler originalCookieHander;
	private Optional<Debugger> debugger = Optional.empty();

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
    
    private final VpnManager<EmbeddedVpnConnection> vpnManager;
    private final AppContext<EmbeddedVpnConnection> app;
    
	private Branding branding;
	private ExecutorService opQueue = Executors.newSingleThreadExecutor();
	private boolean waitingForExitChoice;
	private UI<EmbeddedVpnConnection> ui;
	private Scene scene;
	private Styling styling;
	private Hyperlink back;

	protected AppBar appBar;

	private Image logoImage;

	public Client() {
		var main = Main.getInstance();
        app = main.uiContext(this);
        vpnManager = app.getVpnManager();
        
		back = new Hyperlink();
		back.setGraphic(FontIcon.of(FontAwesome.ARROW_CIRCLE_LEFT, 32));
		back.getStyleClass().add("iconButton");
		back.setOnAction(e -> ui.back());
	}

	public void clearLoadQueue() {
		opQueue.shutdownNow();
		opQueue = Executors.newSingleThreadExecutor();
	}

	public void confirmExit() {
		int active = 0;
		try {
			active = vpnManager.getVpnOrFail().getActiveButNonPersistentConnections();
		} catch (Exception e) {
			exitApp();
		}

		if (active > 0) {
			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.initModality(Modality.APPLICATION_MODAL);
//			alert.initOwner(getStage());
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
						vpnManager.getVpnOrFail().disconnectAll();
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
	public Styling styling() {
		return styling;
	}

	@Override
	public ExecutorService getOpQueue() {
		return opQueue;
	}

	@Override
	public boolean isMinimizeAllowed() {
		return true;
	}

	@Override
	public boolean isTrayConfigurable() {
		return false;
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
		return new CookieManager(app.getCookieStore(), CookiePolicy.ACCEPT_ORIGINAL_SERVER);
	}

	public boolean isWaitingForExitChoice() {
		return waitingForExitChoice;
	}

	@Override
	public void maybeExit() {
		confirmExit();
	}

	@Override
	public void open() {
		// TODO
		log.info("Open request");
	}

	public void options() {
		open();
		Platform.runLater(() -> ui.options());
	}

	public static void main(String[] args) {
		launch();
	}

	@Override
	public void init() {
		styling = new Styling(this);

		Platform.setImplicitExit(false);

		addViewFactory(HOME_VIEW, () -> {

			ui = new UI(this);
			ui.setPrefHeight(600);
            ui.setPrefWidth(400);
			ui.setAvailable();

			this.originalCookieHander = CookieHandler.getDefault();
			updateCookieHandlerState();

			return new View(ui) {

				@Override
				protected void updateAppBar(AppBar appBar) {
					Client.this.appBar = appBar;
					appBar.getStyleClass().add("inverse");
					reloadAppbarImage();
					appBar.getActionItems().add(back);
					applyColors(branding, appBar);
				}
			};
		});

		applyColors(branding, null);
	}

	@Override
	public void postInit(Scene scene) {
		scene.getWindow().setOnCloseRequest(e -> {
			maybeExit();
			e.consume();
		});
		this.scene = scene;
		if (com.gluonhq.attach.util.Platform.isDesktop()) {
			Dimension2D dimension2D = DisplayService.create().map(DisplayService::getDefaultDimensions)
					.orElse(new Dimension2D(640, 480));
			scene.getWindow().setWidth(dimension2D.getWidth());
			scene.getWindow().setHeight(dimension2D.getHeight());
		}
	}

	@Override
	public void exitApp() {
		app.shutdown(false);
		opQueue.shutdown();
		Platform.exit();
	}

	protected Color getBase() {
		if (isDarkMode()) {
			return Color.valueOf(PlatformUtilities.get().getApproxDarkModeColor());
		} else
			return Color.WHITE;
	}

	protected Color getBaseInverse() {
		if (isDarkMode())
			return Color.WHITE;
		else
			return Color.BLACK;
	}

	@Override
	public boolean promptForCertificate(AlertType alertType, String title, String content, String key, String hostname,
			String message, PromptingCertManager mgr) {
		ButtonType reject = new ButtonType(BUNDLE.getString("certificate.confirm.reject"));
		ButtonType accept = new ButtonType(BUNDLE.getString("certificate.confirm.accept"));
		Alert alert = createAlertWithOptOut(alertType, title, BUNDLE.getString("certificate.confirm.header"),
				MessageFormat.format(content, hostname, message),
				BUNDLE.getString("certificate.confirm.savePermanently"), param -> {
					if (param)
						mgr.save(key);
				}, accept, reject);
		alert.initModality(Modality.APPLICATION_MODAL);
		alert.getButtonTypes().setAll(accept, reject);
		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == reject) {
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void applyColors(Branding branding, Parent node) {
		if (node == null && scene != null) {
			node = scene.getRoot();
		}
		if (node == null) {
			log.warn("Not ready for branding!");
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
		settings.setPreferredToasterClassName(SysOutToaster.class.getName());
		settings.setAppName(BUNDLE.getString("appName"));
		settings.setSystemTrayIconMode(SystemTrayIconMode.HIDDEN);

		var css = Client.class.getResource(Client.class.getSimpleName() + ".css").toExternalForm();
		ss.add(css);
	}

	@Override
	public boolean isContextMenuAllowed() {
		return true;
	}

	public boolean isDarkMode() {
		String mode = Configuration.getDefault().darkModeProperty().get();
		if (mode.equals(Configuration.DARK_MODE_AUTO))
			return true; // TODO
		else if (mode.equals(Configuration.DARK_MODE_ALWAYS))
			return true;
		else
			return false;
	}

	@Override
	public void openURL(String url) {
		getHostServices().showDocument(url);
	}

	@Override
	public boolean isAllowBranding() {
		return allowBranding;
	}

	@Override
	public void minimize() {
		// TODO
	}

	@Override
	public JfxAppContext getAppContext() {
		return Main.getInstance();
	}

	@Override
	public Optional<Debugger> debugger() {
		return debugger;
	}

	@Override
	public boolean isUndecoratedWindow() {
		return false;
	}

	@Override
	public void setBackVisible(boolean b) {
		back.setVisible(b);
	}

	@Override
	public void setImage(Image image) {
		if (!Objects.equals(image, this.logoImage)) {
			this.logoImage = image;
			if(appBar != null)
				reloadAppbarImage();
		}
	}


	private void reloadAppbarImage() {
		if (logoImage == null)
			appBar.setTitle(new Label(BUNDLE.getString("title")));
		else {
			var imageView = new ImageView(logoImage);
			imageView.setFitHeight(32);
			imageView.setPreserveRatio(true);
			appBar.setTitle(imageView);
		}
	}

	@Override
	public Image getImage() {
		return logoImage;
	}

	@Override
	public Navigator navigator() {
		return this;
	}

	@Override
	public void back() {
	}
}
