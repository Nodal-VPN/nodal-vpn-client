package com.logonbox.vpn.client.desktop;

import static com.sshtools.jajafx.FXUtil.maybeQueue;
import static javafx.application.Platform.runLater;

import com.install4j.api.launcher.StartupNotification;
import com.logonbox.vpn.client.common.BrandingManager.BrandImage;
import com.logonbox.vpn.client.common.BrandingManager.ImageHandler;
import com.logonbox.vpn.client.common.PromptingCertManager;
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
import com.sshtools.jajafx.JajaFXApp;
import com.sshtools.jajafx.JajaFXAppWindow;

import org.freedesktop.dbus.exceptions.DBusException;
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
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class DesktopVPNApp extends JajaFXApp<DesktopVPN> implements UIContext<VpnConnection>, RemoteUI {

    private final static class AWTBrandImage implements BrandImage {
        private BufferedImage img;

        AWTBrandImage(BufferedImage img) {
            this.img = img;
        }
    }

    static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true").equals("true");

    static Logger log = LoggerFactory.getLogger(DesktopVPNApp.class);

    final static ResourceBundle RESOURCES = ResourceBundle.getBundle(DesktopVPNApp.class.getName());

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

    public static void main(String[] args) {
        launch(args);
    }

    private UI<VpnConnection> ui;
    private Styling styling;
    private Optional<Debugger> debugger = Optional.empty();
    private CookieHandler originalCookieHander;
    private boolean waitingForExitChoice;

    private DesktopVPNAppWindow vpnWindow;

    public DesktopVPNApp() {
        super(UI.class.getResource("logonbox_logo.png"), RESOURCES.getString("title"),
                (DesktopVPN) DesktopVPN.getInstance(), 
                Preferences.userNodeForPackage(UI.class));
        styling = new Styling(this);
    }

    @Override
    public void back() {
        ui.back();
    }

    @Override
    public void confirmExit() {
        var active = 0;
        try {
            active = getAppContext().getVpnManager().getVpnOrFail().getActiveButNonPersistentConnections();
        } catch (Exception e) {
            exitApp();
        }

        if (active > 0) {
            var alert = new Alert(AlertType.CONFIRMATION);
            alert.initModality(Modality.APPLICATION_MODAL);
            alert.initOwner(vpnWindow().stage());
            alert.setTitle(RESOURCES.getString("exit.confirm.title"));
            alert.setHeaderText(RESOURCES.getString("exit.confirm.header"));
            alert.setContentText(RESOURCES.getString("exit.confirm.content"));

            var disconnect = new ButtonType(RESOURCES.getString("exit.confirm.disconnect"));
            var stayConnected = new ButtonType(RESOURCES.getString("exit.confirm.stayConnected"));
            var cancel = new ButtonType(RESOURCES.getString("exit.confirm.cancel"), ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(disconnect, stayConnected, cancel);
            waitingForExitChoice = true;
            try {
                var result = alert.showAndWait();
                getAppContext().getScheduler().execute(() -> {
                    if (result.get() == disconnect) {
                        getAppContext().getVpnManager().getVpnOrFail().disconnectAll();
                    }

                    if (result.get() == disconnect || result.get() == stayConnected) {
                        try {
                            getAppContext().getVpnManager().confirmExit();
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
    public Optional<Debugger> debugger() {
        return debugger;
    }

    @Override
    public void exitApp() {
        getAppContext().shutdown(false);
        Platform.exit();
    }

    @Override
    public JfxAppContext<VpnConnection> getAppContext() {
        return getContainer();
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
                var img = ((AWTBrandImage) bim).img;
                var graphics = (java.awt.Graphics2D) img.getGraphics();
                log.info(String.format("Drawing logo on splash"));
                try {
                    var logoImage = ImageIO.read(logoFile.toFile());
                    if (logoImage == null)
                        throw new IOException(String.format("Failed to load image from %s", logoFile));
                    graphics.drawImage(logoImage, (img.getWidth() - logoImage.getWidth()) / 2,
                            (img.getHeight() - logoImage.getHeight()) / 2, null);
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            }

            @Override
            public void write(BrandImage bim, Path splashFile) throws IOException {
                ImageIO.write(((AWTBrandImage) bim).img, "png", splashFile.toFile());
                log.info(String.format("Custom splash written to %s", splashFile));
            }
        };
    }

    @Override
    public String getObjectPath() {
        return RemoteUI.OBJECT_PATH;
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
        vpnWindow().stage().setIconified(true);
    }

    @Override
    public Navigator navigator() {
        return vpnWindow();
    }

    @Override
    public void open() {
        log.info("Open request");
        UI.maybeRunLater(() -> {
            var primaryStage = vpnWindow().stage();
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
    public boolean promptForCertificate(AlertType alertType, String title, String content, String key, String hostname,
            String message, PromptingCertManager mgr) {
        var reject = new ButtonType(RESOURCES.getString("certificate.confirm.reject"));
        var accept = new ButtonType(RESOURCES.getString("certificate.confirm.accept"));
        var alert = createAlertWithOptOut(alertType, title, RESOURCES.getString("certificate.confirm.header"),
                MessageFormat.format(content, hostname, message),
                RESOURCES.getString("certificate.confirm.savePermanently"), param -> {
                    if (param)
                        mgr.save(key);
                }, accept, reject);
        alert.initModality(Modality.APPLICATION_MODAL);
        var stage = vpnWindow().stage();
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
    public void reapplyBranding() {
        log.info("Re-applying branding.");
        
        var primaryScene = vpnWindow().scene();
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
        var css = DesktopVPNApp.class.getResource(DesktopVPNApp.class.getSimpleName() + ".css").toExternalForm();
        ss.add(css);

        String defaultLogo = UI.class.getResource("logonbox-titlebar-logo.png").toExternalForm();
        if ((branding == null || branding.logo().isEmpty()) && !defaultLogo.equals(navigator().getImage() == null ? null : navigator().getImage().getUrl())) {
            navigator().setImage(new Image(defaultLogo, true));
        } else if (branding != null && branding.logo().isPresent()
                && !defaultLogo.equals(branding.logo().get().toUri().toString())) {
            navigator().setImage(new Image(branding.logo().get().toUri().toString(), true));
        }

    }

    @Override
    public void ping() {
    }

    @Override
    public Styling styling() {
        return styling;
    }

    protected void cleanUp() {
        debugger.ifPresent(d -> d.close());
    }

    @Override
    protected DesktopVPNAppWindow createAppWindow(Stage stage) {
        if(vpnWindow == null) {
            vpnWindow = new DesktopVPNAppWindow(stage, createContent(stage), this);
            stage.setOnCloseRequest(evt -> {
                evt.consume();
                maybeExit();
            });
        }
        return vpnWindow;
    }

    @Override
    protected Node createContent(Stage stage) {
        ui = new UI<>(this);
        ui.getBrandingManager().addBrandingChangeListener((bd) -> {
           runLater(() -> reapplyBranding());
        });
        return ui;
    }

    protected CookieManager createCookieManager() {
        return new CookieManager(getAppContext().getCookieStore(), CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    @Override
    public void needUpdate() {
        maybeQueue(() -> {
            ui.setHtmlPage("updateAvailable.html");
        });
    }

    @Override
    protected void onConfigurePrimaryStage(JajaFXAppWindow<JajaFXApp<DesktopVPN>> wnd, Stage stage) {
        var cfg = Configuration.getDefault();
        var x = cfg.xProperty().get();
        var y = cfg.yProperty().get();
        var h = cfg.hProperty().get();
        var w = cfg.wProperty().get();
        
        Rectangle2D cfgBounds = null;
        if(h > 0 && w > 0) {
            cfgBounds =  new Rectangle2D(x, y, w, h);
        }
         
        wnd.configurePersistentGeometry(new Rectangle2D(300, 400, 512, 512), cfgBounds, bnds -> {
            cfg.xProperty().set((int)bnds.getMinX()); 
            cfg.yProperty().set((int)bnds.getMinY());
            cfg.wProperty().set((int)bnds.getWidth());
            cfg.hProperty().set((int)bnds.getHeight());
        });

        ui.setAvailable();

        var splash = SplashScreen.getSplashScreen();
        if (splash != null) {
            splash.close();
        }

        debugger.ifPresent(d -> d.setup(this));
        wnd.setKeepInBounds(true);
    }

    @Override
    protected void onStarted() {

        if (Boolean.getBoolean("logonbox.vpn.chromeDebug")) {
            debugger = Optional
                    .of(ServiceLoader.load(Debugger.class).findFirst().orElseThrow(() -> new IllegalStateException(
                            "Debugging requested, but not debugger module on classpath / modulepath")));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanUp()));
        try {
            ((DBusVpnManager) getAppContext().getVpnManager()).getAltBus().requestBusName(RemoteUI.BUS_NAME);
            ((DBusVpnManager) getAppContext().getVpnManager()).getAltBus().exportObject(RemoteUI.OBJECT_PATH, this);
        } catch (DBusException dbe) {
            throw new IllegalStateException("Failed to export remote UI.", dbe);
        }

        Platform.setImplicitExit(false);
        ((DesktopVPN)getAppContext()).uiContext(this);

        StartupNotification.registerStartupListener(ui::connectToUri);

        this.originalCookieHander = CookieHandler.getDefault();
        updateCookieHandlerState();
        reapplyBranding();
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

    DesktopVPNAppWindow vpnWindow() {
        return vpnWindow;
    }

}