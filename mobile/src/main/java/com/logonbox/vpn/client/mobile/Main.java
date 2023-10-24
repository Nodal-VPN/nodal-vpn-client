package com.logonbox.vpn.client.mobile;

import com.logonbox.vpn.client.AbstractService;
import com.logonbox.vpn.client.attach.wireguard.MobilePlatformService;
import com.logonbox.vpn.client.common.AbstractClient;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ClientPromptingCertManager;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.PromptingCertManager;
import com.logonbox.vpn.client.common.PromptingCertManager.PromptType;
import com.logonbox.vpn.client.common.api.IVPN;
import com.logonbox.vpn.client.gui.jfx.AppContext;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.logonbox.vpn.client.logging.SimpleLoggerConfiguration;
import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
    private  EmbeddedService srv;

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
        var vpn = new EmbeddedVPN(srv, this);
        setVPN(vpn);
        try {
            srv = new EmbeddedService(BUNDLE, this::getVPNOrFail);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start embedded VPN service.", e);
        }
    }

    @Override
    protected void onLazyInit() throws Exception {
        init();
    }
    
    final class EmbeddedService extends AbstractService<EmbeddedVPNConnection> {

		private Level logLevel = Level.INFO;
		private final Level defaultLogLevel = logLevel;
        private final Supplier<IVPN<EmbeddedVPNConnection>> vpn;

		protected EmbeddedService(ResourceBundle bundle, Supplier<IVPN<EmbeddedVPNConnection>> vpn) throws Exception {
			super(bundle);
			this.vpn = vpn;
			
			log.info("Starting in-process VPN service.");

			if (!buildServices()) {
				throw new Exception("Failed to build DBus services.");
			}

			if (!startServices()) {
				throw new Exception("Failed to start DBus services.");
			}

			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
                public void run() {
					close();
				}
			});

			startSavedConnections();
		}
	    
	    @Override
	    protected Logger getLogger() {
	        return log;
	    }

		@Override
	    protected PlatformServiceFactory createPlatformServiceFactory() {
		    /* Find first supported MobilePlatformService. This will delegate to the actual
		     * service, or one of the native mobile implementations.
		     */
            var layer = getClass().getModule().getLayer();
            Stream<Provider<PlatformServiceFactory>> impls;
            if(layer == null)
                impls = ServiceLoader.load(PlatformServiceFactory.class).stream();
            else
                impls = ServiceLoader.load(layer, PlatformServiceFactory.class).stream();
            return impls.filter(
                    p -> p.get() instanceof MobilePlatformService.MobilePlatformServiceFactory && p.get().isSupported())
                    .findFirst().map(p -> p.get())
                    .orElseThrow(() -> new UnsupportedOperationException(String.format(
                            "The Mobile platform %s not currently supported. There are no mobile platform extensions installed, you may be missing libraries.",
                            System.getProperty("os.name"))));
	    }

		@Override
		public Level getDefaultLevel() {
			return defaultLogLevel;
		}

		@Override
		public void setLevel(Level level) {
			// TODO reconfigure logging?
			this.logLevel = level;
		}
		
		EmbeddedVPNConnection wrapConnection(Connection connection) {
		    return new EmbeddedVPNConnection(srv, connection);
		}

        @Override
        public void fireAuthorize(Connection connection, String authorizeUri) {
            var wrapped = wrapConnection(connection);
            onAuthorize.forEach(a -> a.authorize(wrapped, authorizeUri, connection.getMode()));
        }

        @Override
        public void fireConnectionUpdated(Connection connection) {
            var wrapped = wrapConnection(connection);
            onConnectionUpdated.forEach(c -> c.accept(wrapped));
        }

        @Override
        public void fireConnectionUpdating(Connection connection) {
            var wrapped = wrapConnection(connection);
            onConnectionUpdating.forEach(c -> c.accept(wrapped));
        }

        @Override
        public void fireTemporarilyOffline(Connection connection, Exception reason) {
            var wrapped = wrapConnection(connection);
            onTemporarilyOffline.forEach(c -> c.accept(wrapped, reason.getMessage() == null ? "" : reason.getMessage()));
        }

        @Override
        public void fireConnected(Connection connection) {
            var wrapped = wrapConnection(connection);
            onConnected.forEach(c -> c.accept(wrapped));
        }

        @Override
        public void fireConnectionAdding() {
            onConnectionAdding.forEach(c -> c.run());
        }

        @Override
        public void connectionAdded(Connection connection) {
            var wrapped = wrapConnection(connection);
            onConnectionAdded.forEach(c -> c.accept(wrapped));
        }

        @Override
        public void fireConnectionRemoving(Connection connection) {
            var wrapped = wrapConnection(connection);
            onConnectionRemoving.forEach(c -> c.accept(wrapped));
        }

        @Override
        public void connectionRemoved(Connection connection) {
            onConnectionRemoved.forEach(c -> c.accept(connection.getId()));
        }

        @Override
        public void fireDisconnecting(Connection connection, String reason) {
            var wrapped = wrapConnection(connection);
            onDisconnecting.forEach(c -> c.accept(wrapped, reason));
        }

        @Override
        public void fireDisconnected(Connection connection, String reason) {
            var wrapped = wrapConnection(connection);
            onDisconnected.forEach(c -> c.accept(wrapped, reason));            
        }

        @Override
        public void fireConnecting(Connection connection) {
            var wrapped = wrapConnection(connection);
            onConnecting.forEach(c -> c.accept(wrapped));
        }

        @Override
        public void fireFailed(Connection connection, String message, String cause, String trace) {
            var wrapped = wrapConnection(connection);
            onFailure.forEach(c -> c.failure(wrapped, message, cause, trace));
        }

        @Override
        public void promptForCertificate(PromptType alertType, String title, String content, String key,
                String hostname, String message) {
           getCertManager().promptForCertificate(alertType, title, content, key, hostname, message);
        }

        @Override
        public void fireExit() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void onConfigurationChange(ConfigurationItem<?> item, Object oldValue, Object newValue) {
            onGlobalConfigChanged.forEach(c -> c.accept(item, newValue == null ? "" : newValue.toString()));
        }

        @Override
        protected Optional<IVPN<EmbeddedVPNConnection>> buildVpn() {
            return Optional.of(vpn.get());
        }

	}
	
	static String getOwner() {
	    return System.getProperty("user.name");
	}
}
