package com.logonbox.vpn.client.cutdown;

import com.logonbox.vpn.client.common.BrandingManager.ImageHandler;
import com.logonbox.vpn.client.common.DarkMode;
import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.embedded.EmbeddedVpnConnection;
import com.logonbox.vpn.client.embedded.EmbedderApi;
import com.logonbox.vpn.client.gui.jfx.JavaFXUiConfiguration;
import com.logonbox.vpn.client.gui.jfx.Debugger;
import com.logonbox.vpn.client.gui.jfx.JfxAppContext;
import com.logonbox.vpn.client.gui.jfx.Navigator;
import com.logonbox.vpn.client.gui.jfx.Styling;
import com.logonbox.vpn.client.gui.jfx.UI;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.sshtools.jaul.NoUpdateService;
import com.sshtools.jaul.Phase;
import com.sshtools.jaul.UpdateService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieStore;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Client extends Application implements UIContext<EmbeddedVpnConnection>, Navigator, JfxAppContext<EmbeddedVpnConnection> {

	static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true").equals("true");

	public static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());
	static Logger log = LoggerFactory.getLogger(Client.class);

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
//    private final AppContext<EmbeddedVpnConnection> app;
    
	private boolean waitingForExitChoice;
	private UI<EmbeddedVpnConnection> ui;
	private Scene scene;
	private Styling styling;
	private Hyperlink back;

	private Image logoImage;
    private final ScheduledExecutorService queue;
    private final NoUpdateService updateService;

	public Client() {
		//var main = Main.getInstance();
        //app = main.uiContext(this);
//        vpnManager = app.getVpnManager();
	    
	    try {
    	    var embedder = new EmbedderApi();
    	    vpnManager = embedder.manager();
	    }
	    catch(Exception e) {
	        throw new IllegalStateException("Failed to initialise VPN embedder.", e);
	    }
        
		back = new Hyperlink();
//		back.setGraphic(FontIcon.of(FontAwesome.ARROW_CIRCLE_LEFT, 32));
		back.getStyleClass().add("iconButton");
		back.setOnAction(e -> ui.back());
		queue = Executors.newSingleThreadScheduledExecutor();
		updateService = new NoUpdateService(this);
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
					getAppContext().getScheduler().execute(() -> {
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
    public void start(Stage primaryStage) throws Exception {
        styling = new Styling(this);
        ui = new UI<>(this);
        ui.setPrefHeight(600);
        ui.setPrefWidth(400);
        ui.setAvailable();
//        var scene = new Scene(primaryStage);
//        primaryStage.getScene().ad
//
    }

    @Override
    public boolean isOptions() {
        return false;
    }

	@Override
	public Styling styling() {
		return styling;
	}

	@Override
	public boolean isMinimizeAllowed() {
		return true;
	}

	@Override
	public boolean isTrayConfigurable() {
		return false;
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
    public void startTray() {
    }

    @Override
	public void init() {

//		appManager.addViewFactory(AppManager.HOME_VIEW, () -> {
//
//	        styling = new Styling(this);
//	        ui = new UI<>(this);
//	        ui.setPrefHeight(600);
//	        ui.setPrefWidth(400);
//	        ui.setAvailable();
//
//			return new View(ui) {
//
//				@Override
//				protected void updateAppBar(AppBar appBar) {
//					Client.this.appBar = appBar;
//					appBar.getStyleClass().add("inverse");
//					reloadAppbarImage();
//					appBar.getActionItems().add(back);
//					Platform.runLater(Client.this::reapplyBranding);
//				}
//			};
//		});
	}

	private void postInit(Scene scene) {
		scene.getWindow().setOnCloseRequest(e -> {
			maybeExit();
			e.consume();
		});
		this.scene = scene;
        Platform.setImplicitExit(false);
        reapplyBranding();
	}

	@Override
	public void exitApp() {
		getAppContext().shutdown(false);
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
	public void reapplyBranding() {

        var node = scene.getRoot();
        var branding = ui.getBrandingManager().branding().orElse(null);


		ObservableList<String> ss = node.getStylesheets();

		/* No branding, remove the custom styles if there are any */
		ss.clear();

		/* Create new custom local web styles */
		styling.apply(branding == null ? null : branding.branding());

		/* Create new JavaFX custom styles */
		var tmpFile = styling.getCustomJavaFXCSSFile();
		if (log.isDebugEnabled())
			log.debug(String.format("Using custom JavaFX stylesheet %s", tmpFile));
		var uri = Styling.toUri(tmpFile).toExternalForm();
		ss.add(0, uri);

		var css = Client.class.getResource(Client.class.getSimpleName() + ".css").toExternalForm();
		ss.add(css);
	}

	@Override
	public boolean isContextMenuAllowed() {
		return true;
	}

	public boolean isDarkMode() {
		DarkMode mode = JavaFXUiConfiguration.getDefault().darkModeProperty().get();
		if (mode.equals(DarkMode.AUTO))
			return true; // TODO
		else if (mode.equals(DarkMode.ALWAYS))
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
	public JfxAppContext<EmbeddedVpnConnection> getAppContext() {
		return this;
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

    @Override
    public VpnManager<EmbeddedVpnConnection> getVpnManager() {
        return vpnManager;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public boolean isConnectUri() {
        return false;
    }

    @Override
    public UpdateService getUpdateService() {
        return updateService;
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public String getEffectiveUser() {
        return System.getProperty("user.name");
    }

    @Override
    public PromptingCertManager getCertManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CookieStore getCookieStore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return queue;
    }

    @Override
    public void shutdown(boolean restart) {
    }

    @Override
    public LoggingConfig getLogging() {
        return LoggingConfig.dumb();
    }

    @Override
    public String getUri() {
        return null;
    }

    @Override
    public boolean isConnect() {
        return true;
    }

    @Override
    public boolean isExitOnConnection() {
        return false;
    }

    @Override
    public boolean isNoMinimize() {
        return true;
    }

    @Override
    public boolean isNoClose() {
        return false;
    }

    @Override
    public boolean isNoAddWhenNoConnections() {
        return false;
    }

    @Override
    public boolean isNoResize() {
        return true;
    }

    @Override
    public boolean isNoMove() {
        return true;
    }

    @Override
    public boolean isNoSystemTray() {
        return true;
    }

    @Override
    public boolean isCreateIfDoesntExist() {
        return false;
    }

    @Override
    public ImageHandler getImageHandler() {
        return ImageHandler.dumb();
    }

    @Override
    public boolean isAutomaticUpdates() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setAutomaticUpdates(boolean automaticUpdates) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Phase getPhase() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setPhase(Phase phase) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public long getUpdatesDeferredUntil() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setUpdatesDeferredUntil(long timeMs) {
        // TODO Auto-generated method stub
        
    }
}
