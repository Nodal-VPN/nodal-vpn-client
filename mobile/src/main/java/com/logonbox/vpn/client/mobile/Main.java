package com.logonbox.vpn.client.mobile;

import com.logonbox.vpn.client.attach.wireguard.MobilePlatformService;
import com.logonbox.vpn.client.common.AbstractClient;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ClientPromptingCertManager;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.embedded.EmbeddedService;
import com.logonbox.vpn.client.embedded.EmbeddedVPN;
import com.logonbox.vpn.client.embedded.EmbeddedVPNConnection;
import com.logonbox.vpn.client.gui.jfx.AppContext;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.logonbox.vpn.client.logging.SimpleLoggerConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.nativeimage.annotations.Reflectable;

@Command(name = "lbvpn-mobile-gui", mixinStandardHelpOptions = true, description = "Start the LogonBox VPN graphical user interface.", versionProvider = Main.VersionProvider.class)
public class Main extends AbstractClient<EmbeddedVPNConnection> implements Callable<Integer>, AppContext  {

    @Reflectable
    public final static class VersionProvider implements IVersionProvider {
        
        public VersionProvider() {}
        
        @Override
        public String[] getVersion() throws Exception {
            return new String[] {
                "Mobile GUI: " + AppVersion.getVersion("com.logonbox", "client-logonbox-vpn-mobile"),
                "VPN Library: " + AppVersion.getVersion("com.logonbox", "logonbox-vpn-lib"),
            };
        }
    }

	static Logger log;

	private static  final ResourceBundle BUNDLE = ResourceBundle.getBundle(Main.class.getName());

	public static void main(String[] args) throws Exception {
		uiContext.ifPresentOrElse(c -> c.open(), () -> {
			System.exit(
					new CommandLine(new Main()).execute(args));
		});
	}

	public final static String SID_ADMINISTRATORS_GROUP = "S-1-5-32-544";

    /**
     * Used to get version from Maven meta-data
     */
    public static final String ARTIFACT_COORDS = "com.logonbox/client-logonbox-vpn-mobile";

    private static Main instance;

    @Option(names = { "-C", "--no-close" }, description = "Do not allow the window to be closed manually.")
    private boolean noClose;

    @Option(names = { "-x", "--exit-on-connection" }, description = "Exit on successful connection.")
    private boolean exitOnConnection;

    @Option(names = { "-A", "--no-add-when-no-connections" }, description = "Do not show the Add connection page when no connections are configured.")
    private boolean noAddWhenNoConnections;

    @Option(names = { "-M", "--no-minimize" }, description = "Do not allow the window to be minimized manually.")
    private boolean noMinimize;

    @Option(names = { "-S",
            "--no-systray" }, description = "Do not start the system tray, the application will immediately exit when the window is closed.")
    private boolean noSystemTray;

    @Option(names = { "-B", "--no-sidebar" }, description = "Do not show the burger menu or side menu.")
    private boolean noSidebar;

    @Option(names = { "-R", "--no-resize" }, description = "Do not allow the window to be resized.")
    private boolean noResize;

    @Option(names = { "-D",
            "--no-drag" }, description = "Do not allow the window to be moved. Will be centred on primary monitor.")
    private boolean noMove;

    @Option(names = { "-s", "--size" }, description = "Size of window, in the format <width>X<height>.")
    private String size;

    @Option(names = { "-T", "--always-on-top" }, description = "Keep the window on top of others.")
    private boolean alwaysOnTop;

    @Option(names = { "-U", "--no-updates" }, description = "Do not perform any updates.")
    private boolean noUpdates;

    @Option(names = { "-c", "--connect" }, description = "Connect to the first available pre-configured connection.")
    private boolean connect;

    @Option(names = { "-n", "--create" }, description = "Create a new connection if one with the provided URI does not exist (requires URI parameter).")
    private boolean createIfDoesntExist;

    @Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
    private String uri;

    private Level defaultLogLevel = Level.INFO;

    protected static Optional<UIContext<EmbeddedVPNConnection>> uiContext = Optional.empty();

    public Main() {
        super();

        System.setProperty(SimpleLoggerConfiguration.CONFIGURATION_FILE_KEY, "default-log-mobile.properties");
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        
        instance = this;
        log = LoggerFactory.getLogger(Main.class);

        log.info(String.format("GUI Version : %s", AppVersion.getVersion("com.logonbox", "client-logonbox-vpn-mobile")));
        log.info(String.format("DBus Version: %s", AppVersion.getVersion("com.github.hypfvieh", "dbus-java-core")));
        log.info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")"));
        try {
            log.info(String.format("CWD: %s", new File(".").getCanonicalPath()));
        } catch (IOException e) {
        }
    }

    @Override
    public void confirmExit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer call() throws Exception {
        Client.main(new String[0]);
        return 0;
    }

    @Override
    public final boolean isBusAvailable() {
        return true;
    }

    @Override
    public Level getDefaultLogLevel() {
        return defaultLogLevel;
    }

    @Override
    public boolean isNoAddWhenNoConnections() {
        return noAddWhenNoConnections;
    }

    @Override
    public boolean isExitOnConnection() {
        return exitOnConnection;
    }

    @Override
    public boolean isConnect() {
        return connect;
    }

    @Override
    public String getUri() {
        return uri;
    }

    public boolean isNoUpdates() {
        return noUpdates;
    }

    @Override
    public boolean isNoResize() {
        return noResize;
    }

    public boolean isAlwaysOnTop() {
        return alwaysOnTop;
    }

    @Override
    public boolean isNoMove() {
        return noMove;
    }

    public String getSize() {
        return size;
    }

    @Override
    public boolean isNoClose() {
        return noClose;
    }

    public boolean isNoSidebar() {
        return noSidebar;
    }

    @Override
    public boolean isNoMinimize() {
        return noMinimize;
    }

    @Override
    public boolean isNoSystemTray() {
        return noSystemTray;
    }

    @Override
    public boolean isCreateIfDoesntExist() {
        return createIfDoesntExist;
    }

    public static Main getInstance() {
        return instance;
    }

    @Override
    public void restart() {
        exit();
        System.exit(99);
    }

    public void shutdown() {
        exit();
        System.exit(0);
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    protected PromptingCertManager createCertManager() {
        return new ClientPromptingCertManager(Client.BUNDLE, this) {

            @Override
            public boolean isToolkitThread() {
                return Platform.isFxApplicationThread();
            }

            @Override
            public void runOnToolkitThread(Runnable r) {
                Platform.runLater(r);
            }

            @Override
            public boolean promptForCertificate(PromptType alertType, String title,
                    String content, String key, String hostname, String message) {
                return uiContext.orElseThrow(()-> new IllegalStateException("UI Not registered.")).promptForCertificate(AlertType.valueOf(alertType.name()), title, content, key, hostname, message, this);
            }

        };
    }

    @Override
    public String getVersion() {
        return AppVersion.getVersion("com.logonbox", "client-logonbox-vpn-mobile");
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @SuppressWarnings("unchecked")
    public <M extends AppContext> M uiContext(UIContext<EmbeddedVPNConnection> uiContext) {
        if(Main.uiContext.isPresent())
            throw new IllegalStateException("Already registered.");
        Main.uiContext = Optional.of(uiContext);
        return (M) this;
    }

    @Override
    public void setLevel(Level valueOf) {
    }

    @Override
    public List<EmbeddedVPNConnection> getVPNConnections() {
        return getVPNOrFail().getConnections();
    }

    @Override
    public EmbeddedVPNConnection getVPNConnection(long id) {
        return getVPNOrFail().getConnection(id);
    }

    @Override
    protected void onInit() throws Exception {
        try {
            setVPN(new EmbeddedVPN(new EmbeddedService(this, BUNDLE, this::getVPNOrFail, pf -> pf instanceof MobilePlatformService.MobilePlatformServiceFactory), this));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start embedded VPN service.", e);
        }
    }

    @Override
    protected void onLazyInit() throws Exception {
        init();
    }
}
