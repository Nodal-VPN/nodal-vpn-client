package com.logonbox.vpn.client.mobile;

import com.logonbox.vpn.client.AbstractService;
import com.logonbox.vpn.client.attach.wireguard.MobilePlatformService;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;

import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.connections.impl.DirectConnection;
import org.freedesktop.dbus.connections.impl.DirectConnectionBuilder;
import org.freedesktop.dbus.connections.transports.TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.ResourceBundle;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "logonbox-vpn-gui", mixinStandardHelpOptions = true, description = "Start the LogonBox VPN graphical user interface.")
public class Main extends AbstractMobileMain {
	
	static {
		//System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "Debug");
	}

	static Logger log = LoggerFactory.getLogger(Main.class);
	
	private static  final ResourceBundle BUNDLE = ResourceBundle.getBundle(Main.class.getName());
	private static final String CONNECTION_ADDRESS = TransportBuilder.createDynamicSession("LOOPBACK", false);

	public static void main(String[] args) throws Exception {
		uiContext.ifPresentOrElse(c -> c.open(), () -> {
			System.exit(
					new CommandLine(new Main()).execute(args));
		});
	}

	private EmbeddedService srv;
	
	public Main() {
	}
	
	@Override
	protected AbstractConnection createBusConnection() throws Exception {
		
		srv = new EmbeddedService(BUNDLE, MobilePlatformService.create().get());
		srv.start();
		
		try {
		return DirectConnectionBuilder.forAddress(CONNECTION_ADDRESS).build();
		}
		catch(Exception e) {
			log.error("Failed.", e);
			throw e;
		}
	}
	
	final static class EmbeddedService extends AbstractService {
		
		private Level logLevel = Level.INFO;
		private final Level defaultLogLevel = logLevel;

		protected EmbeddedService(ResourceBundle bundle, PlatformService<VirtualInetAddress<?>> platformService) {
			super(bundle, platformService);
		}
		
		public void start() throws Exception {
			log.info("Starting in-process VPN service.");
			
			if (!buildServices()) {
				throw new Exception("Failed to build DBus services.");
			}

			log.info("Building direct DBus connection");
			var bldr = DirectConnectionBuilder.forAddress(CONNECTION_ADDRESS + ",listen=true");
			bldr.transportConfig().withAutoConnect(false);
			log.info("Creating direct DBus connection");
			
			conn = bldr.build();

			if (!startServices()) {
				throw new Exception("Failed to start DBus services.");
			}

			if (!publishDefaultServices()) {
				throw new Exception("Failed to publish DBus services.");
			}
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					close();
				}
			});
			

			log.info("Listening for direct DBus connections");
			new Thread() {
				public void run() {
					try {
						conn.connect();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}.start();
			
			Thread.sleep(500);
			((DirectConnection)conn).listen();
			
			log.info("Ready for direct DBus connections");
			startSavedConnections();
		}

		@Override
		public boolean isRegistrationRequired() {
			return false;
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
		
	}
}
