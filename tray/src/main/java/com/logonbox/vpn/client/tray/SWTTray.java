/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.logonbox.vpn.client.tray;

import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.TrayMode;
import com.logonbox.vpn.client.common.UiConfiguration;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.sshtools.liftlib.OS;
import com.sshtools.twoslices.ToasterFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TrayItem;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SWTTray extends AbstractTray {

	private List<MenuItem> menuEntries = new ArrayList<>();
	private List<Menu> menus = new ArrayList<>();
	private org.eclipse.swt.widgets.Tray systemTray;
	private TrayItem trayIcon;
	private Display display;
	private Shell shell;
	private Menu popupMenu;
	private Image disposableImage;

	public SWTTray(TrayDaemon context) throws Exception {
		super(context);
		display = Display.getDefault();
		shell = new Shell(display, SWT.NONE);
		display.asyncExec(() -> {
			var connected = context.getVpnManager().isBackendAvailable();
            List<VpnConnection> conx = connected ? context.getVpnManager().getVpn().map(vpn -> Arrays.asList(vpn.getConnections())).orElse(Collections.emptyList()) : Collections.emptyList();
			adjustTray(connected, conx);
		});
	}

	@Override
	public void onClose() throws Exception {
		if (trayIcon != null) {
			var tr = trayIcon;
			display.asyncExec(() -> {
				tr.dispose();
			});
			trayIcon = null;
		}
		systemTray = null;
	}

	public boolean isActive() {
		return systemTray != null;
	}

	public void reload() {
		display.asyncExec(() -> {
			try {
				var connected = context.getVpnManager().isBackendAvailable();
				List<VpnConnection> conx = connected ? context.getVpnManager().getVpn().map(vpn -> Arrays.asList(vpn.getConnections())).orElse(Collections.emptyList()) : Collections.emptyList();
				rebuildMenu(connected, conx);
				setImage(connected, conx);
			} catch (Exception re) {
				display.asyncExec(() -> {
					rebuildMenu(false, Collections.emptyList());
					if (systemTray != null)
						setImage(false, Collections.emptyList());
				});
			}
		});
	}

	public void loop() {
		try {
			while (!shell.isDisposed()) {
				try {
					if (!display.readAndDispatch())
						display.sleep();
				} catch (Throwable e) {
					if (!display.isDisposed()) {
						// SWTUtil.showError("SWT", e.getMessage());
					} else {
						break;
					}
				}
			}

		} finally {
			// quit(false);
		}
	}

	Menu addDevice(VpnConnection device, Menu menu, List<VpnConnection> devs) throws IOException {

		/* Open */
		Type status = Type.valueOf(device.getStatus());
		if (status == Type.CONNECTED) {
			var disconnectDev = new MenuItem(menu, SWT.PUSH);
			disconnectDev.setText(bundle.getString("disconnect"));
			disconnectDev.setAccelerator('d');
			disconnectDev.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					context.getScheduler().execute(() -> device.disconnect(""));
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
			menuEntries.add(disconnectDev);
		} else if (devs.size() > 0 && status == Type.DISCONNECTED) {
			var openDev = new MenuItem(menu, SWT.PUSH);
			openDev.setText(bundle.getString("connect"));
			openDev.setAccelerator('c');
			openDev.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					context.getScheduler().execute(() -> device.connect());
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
			menuEntries.add(openDev);
		}

		return menu;
	}

	void adjustTray(boolean connected, List<VpnConnection> devs) {
		TrayMode icon = UiConfiguration.get().getValue(null, UiConfiguration.TRAY_MODE);
		if (systemTray == null && !Objects.equals(icon, TrayMode.OFF)) {
			disposeIcon();
			clearMenus();
			if (popupMenu != null)
				popupMenu.dispose();
			systemTray = display.getSystemTray();
			if (systemTray == null) {
				throw new RuntimeException("Unable to load SystemTray!");
			}
			trayIcon = new TrayItem(systemTray, SWT.NONE);
			setImage(connected, devs);
			trayIcon.setToolTipText(bundle.getString("title"));

			popupMenu = new Menu(shell, SWT.POP_UP);
			rebuildMenu(connected, devs);
			trayIcon.addListener(SWT.MenuDetect, (e) -> popupMenu.setVisible(true));

			var settings = ToasterFactory.getSettings();
			if (OS.isMacOs()) {
				settings.setParent(trayIcon);
			}

		} else if (systemTray != null && TrayMode.OFF.equals(icon)) {
			disposeIcon();
			systemTray = null;
		} else if (systemTray != null) {
			setImage(connected, devs);
			rebuildMenu(connected, devs);
		}
	}

	private void disposeImage() {
		if (disposableImage != null) {
			disposableImage.dispose();
			disposableImage = null;
		}
	}

	private void disposeIcon() {
		disposeImage();
		if (trayIcon != null) {
			trayIcon.dispose();
			trayIcon = null;
		}
	}

	private void clearMenus() {
		if (systemTray != null) {
			for (var i : menuEntries)
				i.dispose();
			for (var i : menus)
				i.dispose();
		}
		menuEntries.clear();
		menus.clear();
	}

	private void rebuildMenu(boolean connected, List<VpnConnection> devs) {
		clearMenus();
		if (systemTray != null) {

			var open = new MenuItem(popupMenu, SWT.PUSH);
			open.setText(bundle.getString("open"));
			open.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					context.open();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
			open.setAccelerator('o');
			menuEntries.add(open);
			menuEntries.add(new MenuItem(popupMenu, SWT.SEPARATOR));

			try {
				boolean devices = false;
				if (connected) {
					if (devs.size() == 1) {
						addDevice(devs.get(0), popupMenu, devs);
						devices = true;
					} else {
						for (var dev : devs) {
							var devitem = new MenuItem(popupMenu, SWT.CASCADE);
							var devmenu = new Menu(popupMenu);
							devitem.setMenu(devmenu);
							devitem.setText(dev.getDisplayName());
							addDevice(dev, devmenu, devs);
							menus.add(devmenu);
							menuEntries.add(devitem);
							devices = true;
						}
					}
				}
				if (devices)
					menuEntries.add(new MenuItem(popupMenu, SWT.SEPARATOR));
			} catch (Exception e) {
				// TODO add error item / tooltip?
				trayIcon.setToolTipText("Error!");
				e.printStackTrace();
			}

			var options = new MenuItem(popupMenu, SWT.PUSH);
			options.setText(bundle.getString("options"));
			options.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					context.options();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
			options.setAccelerator('p');
			menuEntries.add(options);

			var quit = new MenuItem(popupMenu, SWT.PUSH);
			quit.setText(bundle.getString("quit"));
			quit.setAccelerator('q');
			quit.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					context.confirmExit();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
			menuEntries.add(quit);
		}
	}

	private void setImage(boolean connected, List<VpnConnection> devs) {
		disposeImage();
		try {
			Image image;
			if (context.getVpnManager().isBackendAvailable()) {
			    
	            if(Boolean.getBoolean("logonbox.vpn.compositeTrayIcon")) {

                    TrayMode icon = UiConfiguration.get().getValue(null, UiConfiguration.TRAY_MODE);
                    if (TrayMode.LIGHT.equals(icon)) {
                        disposableImage = image = overlay(SWTTray.class.getResource("light-logonbox-icon64x64.png"), 48,
                                devs);
                    } else if (TrayMode.DARK.equals(icon)) {
                        disposableImage = image = overlay(SWTTray.class.getResource("dark-logonbox-icon64x64.png"),
                                DEFAULT_ICON_SIZE, devs);
                    } else if (TrayMode.COLOR.equals(icon)) {
                        disposableImage = image = overlay(SWTTray.class.getResource("color-logonbox-icon64x64.png"), 48,
                                devs);
                    } else {
                        if (isDark())
                            disposableImage = image = overlay(SWTTray.class.getResource("light-logonbox-icon64x64.png"), 48,
                                    devs);
                        else
                            disposableImage = image = overlay(SWTTray.class.getResource("dark-logonbox-icon64x64.png"), 48,
                                    devs);
                    }
                    
			    }
			    else {

                    TrayMode icon = UiConfiguration.get().getValue(null, UiConfiguration.TRAY_MODE);
                    if (TrayMode.LIGHT.equals(icon)) {
                        disposableImage = image = overlay("light-logonbox-icon", 64, devs);
                    } else if (TrayMode.DARK.equals(icon)) {
                        disposableImage = image = overlay("dark-logonbox-icon", 64, devs);
                    } else if (TrayMode.COLOR.equals(icon)) {
                        disposableImage = image = overlay("color-logonbox-icon", 64, devs);
                    } else {
                        if (isDark())
                            disposableImage = image = overlay("light-logonbox-icon", 64, devs);
                        else
                            disposableImage = image = overlay("dark-logonbox-icon", 64, devs);
                    }
			    }
			} else {
				image = display.getSystemImage(SWT.ICON_ERROR);
			}
			trayIcon.setImage(image);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    protected Image overlay(String resource, int sz, List<VpnConnection> devs) {

        try {
            resource += sz + "x" + sz;
            
            int connecting = 0;
            int connected = 0;
            int authorizing = 0;
            int total = 0;
            for (var s : devs) {
                Type status = Type.valueOf(s.getStatus());
                if (status == Type.CONNECTED)
                    connected++;
                else if (status == Type.AUTHORIZING)
                    authorizing++;
                else if (status == Type.CONNECTING)
                    connecting++;
                total++;
            }
            if (total > 0) {
                if (authorizing > 0)
                    resource += "-blue";
                else if (connecting > 0)
                    resource += "-blue";
                else if (connected == total)
                    resource += "-green";
                else if (connected > 0)
                    resource += "-dark-green";
                else if (total > 0)
                    resource += "-red";
            }
            
            resource += ".png";

            try (var in = SWTTray.class.getResource(resource).openStream()) {
                return new Image(display, in);
            } catch (IOException ioe) {
                throw new IllegalStateException("Failed to load image " + resource + ".");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load image.", e);
        }
    }

	protected Image overlay(URL resource, int sz, List<VpnConnection> devs) {
		Image image;
		try (var in = resource.openStream()) {
			image = new Image(display, in);
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed to load image " + resource + ".");
		}

		try {
			int connecting = 0;
			int connected = 0;
			int authorizing = 0;
			int total = 0;
			for (var s : devs) {
				Type status = Type.valueOf(s.getStatus());
				if (status == Type.CONNECTED)
					connected++;
				else if (status == Type.AUTHORIZING)
					authorizing++;
				else if (status == Type.CONNECTING)
					connecting++;
				total++;
			}
			Color col = null;
			if (total > 0) {
				if (authorizing > 0)
					col = display.getSystemColor(SWT.COLOR_BLUE);
				else if (connecting > 0)
					col = display.getSystemColor(SWT.COLOR_BLUE);
				else if (connected == total)
					col = display.getSystemColor(SWT.COLOR_GREEN);
				else if (connected > 0)
					col = display.getSystemColor(SWT.COLOR_DARK_GREEN);
				else if (total > 0)
					col = display.getSystemColor(SWT.COLOR_RED);
			}

			if (col != null) {
				final Image newImage = createTransparentImage(display, image.getImageData().width,
						image.getImageData().height);
				final GC gc = new GC(newImage);
				gc.setAntialias(SWT.ON);
				gc.drawImage(image, 0, 0);
				gc.setBackground(col);
				gc.fillOval(0, 0, image.getImageData().width / 3, image.getImageData().height / 3);
				gc.dispose();
				image.dispose();
				image = newImage;
			}
			return image;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load image.", e);
		}
	}

	@Override
	protected boolean isDefaultDark() {
		return Display.isSystemDarkTheme();
	}

	static Image createTransparentImage(Display display, int width, int height) {
		// allocate an image data
		ImageData imData = new ImageData(width, height, 24, new PaletteData(0xff0000, 0x00ff00, 0x0000ff));
		imData.setAlpha(0, 0, 0); // just to force alpha array allocation with the right size
		Arrays.fill(imData.alphaData, (byte) 0); // set whole image as transparent

		// Initialize image from transparent image data
		return new Image(display, imData);
	}
}
