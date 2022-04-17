package com.logonbox.vpn.client.gui.swt;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.CustomCookieStore;
import com.logonbox.vpn.common.client.PromptingCertManager.PromptType;
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.dbus.RemoteUI;

import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class Client implements RemoteUI {

	public enum Resize {
		TL, T, TR, L, R, BL, B, BR
	}

	static final boolean allowBranding = System.getProperty("logonbox.vpn.allowBranding", "true").equals("true");

	static ResourceBundle BUNDLE = ResourceBundle.getBundle(Client.class.getName());

	static UUID localWebServerCookie = UUID.randomUUID();

	static final String LOCBCOOKIE = "LOCBCKIE";
	static Logger log = LoggerFactory.getLogger(Client.class);

	private CookieHandler originalCookieHander;

	static URL toUri(File tmpFile) {
		try {
			return tmpFile.toURI().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	private Branding branding;
	private ExecutorService opQueue = Executors.newSingleThreadExecutor();
	private Shell shell;
	private boolean waitingForExitChoice;
	private UI ui;
	private Display display;
	private Main main;
	private Resize resize;
	private Boolean blnMouseDown = false;
	private int xPos = 0;
	private int yPos = 0;

	public Client(Main main, Display display) {
		this.display = display;
		this.main = main;
	}

	public void clearLoadQueue() {
		opQueue.shutdownNow();
		opQueue = Executors.newSingleThreadExecutor();
	}

	@Override
	public void confirmExit() {
		display.asyncExec(() -> {
			int active = 0;
			try {
				active = getDBus().getVPN().getActiveButNonPersistentConnections();
				log.info("{} non-persistent connections", active);
			} catch (Exception e) {
				exitApp();
			}

			if (active > 0) {
				var alert = new TitledMessageBox(shell) {
					@Override
					protected void createButtonsForButtonBar(Composite parent) {
						createButton(parent, IDialogConstants.OK_ID, BUNDLE.getString("exit.confirm.disconnect"), true);
						createButton(parent, IDialogConstants.SKIP_ID, BUNDLE.getString("exit.confirm.stayConnected"),
								false);
						createButton(parent, IDialogConstants.CANCEL_ID, BUNDLE.getString("exit.confirm.cancel"),
								false);
					}

					@Override
					protected void buttonPressed(int buttonId) {
						if (IDialogConstants.SKIP_ID == buttonId) {
							setReturnCode(99);
							close();
						} else
							super.buttonPressed(buttonId);
					}
				};
				alert.setTitle(BUNDLE.getString("exit.confirm.title"));
				alert.setMessage(BUNDLE.getString("exit.confirm.header"), IMessageProvider.INFORMATION);
				alert.setContent(BUNDLE.getString("exit.confirm.content"));

				waitingForExitChoice = true;
				try {
					if (alert.open() == Window.OK) {
						var result = alert.getReturnCode();
						opQueue.execute(() -> {
							if (result == TitledMessageBox.OK) {
								getDBus().getVPN().disconnectAll();
							}

							if (result == TitledMessageBox.OK || result == 99) {
								try {
									getDBus().getBus().sendMessage(new ConfirmedExit(RemoteUI.OBJECT_PATH));
									Thread.sleep(1000);
								} catch (Exception e) {
								}
								exitApp();
							}
						});
					}
				} finally {
					waitingForExitChoice = false;
				}
			} else {
				exitApp();
			}
		});
	}

	public AbstractDBusClient getDBus() {
		return main;
	}

	public ExecutorService getOpQueue() {
		return opQueue;
	}

	public Shell getStage() {
		return shell;
	}

	public void init() throws Exception {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				display.asyncExec(() -> cleanUp());
			}
		});
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
		boolean wantsPeristJar = Configuration.getDefault().saveCookiesProperty();
		if (default1 == null || isPersistJar != wantsPeristJar) {
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
		confirmExit();
	}

	@Override
	public void open() {
		log.info("Open request");
		display.asyncExec(() -> {
			if (shell.getMinimized())
				shell.setMinimized(false);
			shell.open();
			shell.forceActive();
		});
	}

	@Override
	public void options() {
		open();
		display.asyncExec(() -> ui.options());
	}

	public void start() throws Exception {
		this.originalCookieHander = CookieHandler.getDefault();
		if (this.originalCookieHander == null)
			this.originalCookieHander = new CookieManager();
		updateCookieHandlerState();

		int flags = main.isWindowDecorations() ? SWT.SHELL_TRIM : SWT.NO_TRIM;
		if (!main.isNoMove() && !main.isNoResize())
			flags |= SWT.RESIZE;
		if (main.isAlwaysOnTop())
			flags |= SWT.ON_TOP;

		this.shell = new Shell(display, flags);
//		detector = OsThemeDetector.getDetector();

		// Setup the window
//		if (Platform.isSupported(ConditionalFeature.TRANSPARENT_WINDOW)) {
//			primaryStage.initStyle(StageStyle.TRANSPARENT);
//		} else {
//			primaryStage.initStyle(StageStyle.UNDECORATED);
//		}
		shell.setText(BUNDLE.getString("title"));
		shell.setImages(new Image[] { new Image(display, getClass().getResourceAsStream("logonbox-icon256x256.png")),
				new Image(display, getClass().getResourceAsStream("logonbox-icon128x128.png")),
				new Image(display, getClass().getResourceAsStream("logonbox-icon64x64.png")),
				new Image(display, getClass().getResourceAsStream("logonbox-icon48x48.png")),
				new Image(display, getClass().getResourceAsStream("logonbox-icon32x32.png")) });

		// Open the actual scene
		ui = new UI(shell, this, main, display);
		ui.configure();

		// For line store border
//		node.styleProperty().set("-fx-border-color: -fx-lbvpn-background;");

		applyColors(branding, ui.getScene());

		// Finalise and show
		Configuration cfg = Configuration.getDefault();

		int x = cfg.xProperty();
		int y = cfg.yProperty();
		int h = cfg.hProperty();
		int w = cfg.wProperty();
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
			Rectangle screenBounds = display.getPrimaryMonitor().getBounds();
			x = (int) (screenBounds.x + ((screenBounds.width - w) / 2));
			y = (int) (screenBounds.y + ((screenBounds.height - h) / 2));
		}
		shell.setLocation(x, y);
		shell.setSize(w, h);
		shell.open();

		if (!main.isNoMove() && !main.isWindowDecorations()) {
			ui.getTop().addMouseListener(new MouseListener() {
				@Override
				public void mouseUp(MouseEvent arg0) {
					blnMouseDown = false;
				}

				@Override
				public void mouseDown(MouseEvent e) {
					blnMouseDown = true;
					xPos = e.x;
					yPos = e.y;
				}

				@Override
				public void mouseDoubleClick(MouseEvent arg0) {
				}
			});
			ui.getTop().addMouseMoveListener(new MouseMoveListener() {
				@Override
				public void mouseMove(MouseEvent e) {

				}
			});

			int edgeSize = 8;

			display.addFilter(SWT.MouseMove, (e) -> {

				var ey = e.y;
				var ex = e.x;
				if (e.widget instanceof Control) {
					Point absolutePos = ((Control) e.widget).toDisplay(ex, ey);
					ex = absolutePos.x - shell.getLocation().x;
					ey = absolutePos.y - shell.getLocation().y;
				}

				if (!blnMouseDown || resize == null) {

					if (ey < edgeSize) {
						if (ex < edgeSize) {
							resize = Resize.TL;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_SIZENW));
						} else if (ex > shell.getSize().x - edgeSize) {
							resize = Resize.TR;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_SIZENE));
						} else {
							resize = Resize.T;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_SIZEN));
						}
					} else if (ey >= shell.getSize().y - edgeSize) {
						if (ex < edgeSize) {
							resize = Resize.BL;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_SIZESW));
						} else if (ex >= shell.getSize().x - edgeSize) {
							resize = Resize.BR;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_SIZESE));
						} else {
							resize = Resize.B;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_SIZES));
						}
					} else {
						if (ex < edgeSize) {
							resize = Resize.L;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_SIZEW));
						} else if (ex >= shell.getSize().x - edgeSize) {
							resize = Resize.R;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_SIZEE));
						} else {
							resize = null;
							shell.setCursor(display.getSystemCursor(SWT.CURSOR_ARROW));
						}
					}
				}

				var dx = e.x - xPos;
				var dy = e.y - yPos;
				if(resize != null || blnMouseDown) {
					System.out.println("rsz: " + resize + " " + blnMouseDown);
				}
				if (resize == null) {
					if (blnMouseDown) {
						shell.setLocation(shell.getLocation().x + dx, shell.getLocation().y + dy);
					}
				} else {
					if (blnMouseDown) {
						switch (resize) {
						case T:
							shell.setSize(shell.getSize().x, shell.getSize().y - dy);
							shell.setLocation(shell.getLocation().x, shell.getLocation().y + dy);
							break;
						case L:
							shell.setLocation(shell.getLocation().x - dx, shell.getLocation().y);
							shell.setSize(shell.getSize().x + dx, shell.getSize().y);
							break;
						case R:
							shell.setSize(shell.getSize().x - dx, shell.getSize().y);
							break;
						}
					}
				}
			});
		}

		/* Dark mode handling */
//		cfg.darkModeProperty().addListener((c, o, n) -> {
//			reapplyColors();
//		});
//		detector.registerListener(isDark -> {
//			Platform.runLater(() -> {
//				log.info("Dark mode is now " + isDark);
//				reapplyColors();
//				ui.reload();
//			});
//		});
		shell.addShellListener(new ShellListener() {

			@Override
			public void shellIconified(ShellEvent e) {
			}

			@Override
			public void shellDeiconified(ShellEvent e) {
			}

			@Override
			public void shellDeactivated(ShellEvent e) {
			}

			@Override
			public void shellClosed(ShellEvent e) {
				if (!main.isNoClose())
					confirmExit();
				e.doit = false;
			}

			@Override
			public void shellActivated(ShellEvent e) {
			}
		});

		ui.setAvailable();

		keepInBounds(shell);
//		Screen.getScreens().addListener(new ListChangeListener<Screen>() {
//			@Override
//			public void onChanged(Change<? extends Screen> c) {
//				keepInBounds(primaryStage);
//			}
//		});

//		Configuration.getDefault().saveCookiesProperty().addListener((e) -> updateCookieHandlerState());

	}

	private void keepInBounds(Shell primaryStage) {
		var pbounds = primaryStage.getBounds();
		var screens = display.getMonitors();
		var screen = screens[0];
		Rectangle bounds = screen.getBounds();
		log.info(String.format("Moving into bounds %s from %d,%d", bounds, pbounds.x, pbounds.y));
		if (pbounds.x < bounds.x) {
			primaryStage.setLocation(bounds.x, pbounds.y);
		} else if (pbounds.x + pbounds.width > bounds.x + bounds.width) {
			primaryStage.setLocation((bounds.x + bounds.width) - pbounds.width, pbounds.y);
		}
		if (pbounds.y < bounds.y) {
			primaryStage.setLocation(pbounds.x, bounds.y);
		} else if (pbounds.y + pbounds.height > (bounds.y + bounds.height)) {
			primaryStage.setLocation(pbounds.x, (bounds.y + bounds.height) - pbounds.height);
		}
	}

	@Override
	public String getObjectPath() {
		return RemoteUI.OBJECT_PATH;
	}

	protected void cleanUp() {
	}

	@Override
	public void exitApp() {
		var cfg = Configuration.getDefault();
		cfg.xProperty(getStage().getLocation().x);
		cfg.yProperty(getStage().getLocation().y);
		cfg.wProperty(getStage().getSize().x);
		cfg.hProperty(getStage().getSize().y);
		main.exit();
		opQueue.shutdown();
		System.exit(0);
	}

	protected RGBA getBase() {
		if (isDarkMode()) {
			if (SystemUtils.IS_OS_LINUX)
				return Colors.valueOf("#1c1f22");
			else if (SystemUtils.IS_OS_MAC_OSX)
				return Colors.valueOf("#231f25");
			else
				return Colors.valueOf("#202020");
		} else
			return display.getSystemColor(SWT.COLOR_WHITE).getRGBA();
	}

	protected RGBA getBaseInverse() {
		if (isDarkMode())
			return display.getSystemColor(SWT.COLOR_WHITE).getRGBA();
		else
			return display.getSystemColor(SWT.COLOR_BLACK).getRGBA();
	}

	protected boolean promptForCertificate(PromptType alertType, String title, String content, String key,
			String hostname, String message, Preferences preference) {
		var alert = new TitledMessageBoxWithCheckbox(shell) {
			@Override
			protected void createButtonsForButtonBar(Composite parent) {
				createButton(parent, IDialogConstants.OK_ID, BUNDLE.getString("certificate.confirm.accept"), true);
				createButton(parent, IDialogConstants.CANCEL_ID, BUNDLE.getString("certificate.confirm.reject"), false);
			}

			@Override
			protected void buttonPressed(int buttonId) {
				if (IDialogConstants.SKIP_ID == buttonId) {
					setReturnCode(99);
					close();
				} else
					super.buttonPressed(buttonId);
			}
		};
		alert.setTitle(title);
		alert.setMessage(BUNDLE.getString("certificate.confirm.header"), IMessageProvider.INFORMATION);
		alert.setContent(MessageFormat.format(content, hostname, message));

		try {
			if (alert.open() == Window.OK) {
				return alert.getReturnCode() == TitledMessageBox.OK;
			}
		} finally {
			preference.putBoolean(key, true);
		}
		return false;
	}

	void applyColors(Branding branding, Composite node) {
		if (node == null && ui != null)
			node = ui.getScene();

		this.branding = branding;

		/* Create new custom local web styles */
		File tmpFile = getCustomLocalWebCSSFile();
		tmpFile.getParentFile().mkdirs();
		String url = toUri(tmpFile).toExternalForm();
		if (log.isDebugEnabled())
			log.debug(String.format("Writing local web style sheet to %s", url));

		var colors = new Colors(branding, getBase());
		ui.applyColors(colors);

		String accent1Str = Colors.toHex(Colors.deriveRGBA(colors.getBgColor(), 0f, 1f, 0.85f, 1f));
		String accent2Str = Colors.toHex(Colors.deriveRGBA(colors.getBgColor(), 0f, 1f, 1.15f, 1f));
		String baseStr = Colors.toHex(colors.getBaseColor());
		String baseInverseStr = Colors.toHex(getBaseInverse());
		String linkStr = Colors.toHex(colors.getLinkColor());
		String baseInverseRgbStr = Colors.toRgba(getBaseInverse().rgb, 0.05d);
		try (var output = new PrintWriter(new FileWriter(tmpFile))) {
			try (var input = new BufferedReader(
					new InputStreamReader(UI.class.getResource("local.css").openStream()))) {
				String line;
				while ((line = input.readLine()) != null) {
					line = line.replace("${lbvpnBackground}", Colors.toHex(colors.getBgColor()));
					line = line.replace("${lbvpnForeground}", Colors.toHex(colors.getFgColor()));
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

	public File getTempDir() {
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			return new File(System.getProperty("java.io.tmpdir"));
		else
			return new File(System.getProperty("hypersocket.bootstrap.distDir")).getParentFile();
	}

	File getCustomLocalWebCSSFile() {
		File tmpFile;
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			tmpFile = new File(getTempDir(), System.getProperty("user.name") + "-lbvpn-web.css");
		else
			tmpFile = new File(getTempDir(), "lbvpn-web.css");
		return tmpFile;
	}

	boolean isDarkMode() {
		String mode = Configuration.getDefault().darkModeProperty();
		if (mode.equals(Configuration.DARK_MODE_AUTO))
			// return detector.isDark();
			return true;
		else if (mode.equals(Configuration.DARK_MODE_ALWAYS))
			return true;
		else
			return false;
	}

	public void openURL(String url) throws IOException {
		if (SystemUtils.IS_OS_LINUX) {
			Runtime.getRuntime().exec(new String[] { "xdg-open", url });
		} else
			throw new UnsupportedOperationException("TODO");
	}

	class TitledMessageBox extends TitleAreaDialog {

		private String content;

		public TitledMessageBox(Shell parentShell) {
			super(parentShell);
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		@Override
		public void create() {
			super.create();
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			Label container = new Label(area, SWT.WRAP);
			container.setText(content);
			container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			return area;
		}

	}

	class TitledMessageBoxWithCheckbox extends TitledMessageBox {
		private Button savePermanently;

		public TitledMessageBoxWithCheckbox(Shell parentShell) {
			super(parentShell);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			savePermanently = new Button(area, SWT.CHECK);
			savePermanently.setText(BUNDLE.getString("certificate.confirm.savePermanently"));
			savePermanently.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			return area;
		}

		public boolean isSavePermanently() {
			return savePermanently.getSelection();
		}
	}
}
