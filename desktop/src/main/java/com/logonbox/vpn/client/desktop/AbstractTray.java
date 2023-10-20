package com.logonbox.vpn.client.desktop;

import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.VpnManager.Handle;
import com.logonbox.vpn.client.common.api.IVPNConnection;
import com.logonbox.vpn.client.gui.jfx.Configuration;
import com.logonbox.vpn.client.gui.jfx.Tray;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.fontawesome.FontAwesomeIkonHandler;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

public abstract class AbstractTray implements AutoCloseable, Tray {

	protected static final int DEFAULT_ICON_SIZE = 48;
	
	protected Client context;
	private Font font;

	private boolean constructing;
	private final List<Handle> listeners;
	
	public AbstractTray(Client context) throws Exception {
		constructing = true;
		this.context = context;
		
		try {
		    var mgr = context.getManager();
		    listeners = Arrays.asList(
	            mgr.onVpnGone(this::reload),
                mgr.onVpnAvailable(() -> {
                    if(!constructing)
                        reload();
                }),
		        mgr.onConnectionAdded(conx -> reload()),
                mgr.onConnectionUpdated(conx -> reload()),
                mgr.onConnectionRemoved(id -> reload()),
                mgr.onConnecting(conx -> reload()),
                mgr.onConnected(conx -> reload()),
                mgr.onDisconnecting((conx,reason) -> reload()),
                mgr.onDisconnected((conx, reason) -> reload()),
                mgr.onTemporarilyOffline((conx, reason) -> reload())
		    );
		    
			Configuration.getDefault().trayModeProperty().addListener((e, o, n) -> {
				reload();
			});
		}
		finally {
			constructing = false;
		}
	}

	@Override
	public final void close() throws Exception {
	    listeners.forEach(l -> l.close());
		onClose();
	}

	@Override
	public void setProgress(int progress) {
	}

	@Override
	public void setAttention(boolean enabled, boolean critical) {
	}

	protected abstract void onClose() throws Exception;

	protected BufferedImage createAwesomeIcon(FontAwesome string, int sz) {
		return createAwesomeIcon(string, sz, 100);
	}

	protected BufferedImage createAwesomeIcon(FontAwesome string, int sz, int opac) {
		return createAwesomeIcon(string, sz, opac, null, 0);
	}

	protected BufferedImage createAwesomeIcon(FontAwesome fontAwesome, int sz, int opac, Color col, double rot) {
		var bim = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
		var graphics = (Graphics2D) bim.getGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		var szf = (int) ((float) sz * 1f);
		var str = Character.toString(fontAwesome.getCode());
		var handler = new FontAwesomeIkonHandler();
		if (font == null) {
			try {
				var stream = handler.getFontResourceAsStream();
				font = Font.createFont(Font.TRUETYPE_FONT, stream);
				GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
				stream.close();
				handler.setFont(font);
			} catch (FontFormatException | IOException ffe) {
				throw new IllegalStateException(ffe);
			}
		}
		var fnt = font.deriveFont((float) szf);
		graphics.setFont(fnt);
		if (opac != 100) {
			graphics.setComposite(makeComposite((float) opac / 100.0f));
		}
		var icon = Configuration.getDefault().trayModeProperty().get();
		if (col == null) {
			if (Configuration.TRAY_MODE_LIGHT.equals(icon)) {
				graphics.setColor(Color.WHITE);
			} else if (Configuration.TRAY_MODE_DARK.equals(icon)) {
				graphics.setColor(Color.BLACK);
			}
		} else {
			graphics.setColor(col);
		}

		graphics.translate(sz / 2, sz / 2);
		graphics.rotate(Math.toRadians(rot));
		graphics.translate(-(graphics.getFontMetrics().stringWidth(str) / 2f),
				(graphics.getFontMetrics().getHeight() / 3f));
		graphics.drawString(str, 0, 0);
		return bim;
	}

	protected AlphaComposite makeComposite(float alpha) {
		int type = AlphaComposite.SRC_OVER;
		return (AlphaComposite.getInstance(type, alpha));
	}

	protected boolean isDark() {
		return context.isDarkMode();
	}

	protected Image overlay(URL resource, int sz, List<IVPNConnection> devs) {
		try {
			int connecting = 0;
			int connected = 0;
			int authorizing = 0;
			int total = 0;
			for (IVPNConnection s : devs) {
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
					col = Color.GREEN;
				else if (connecting > 0)
					col = Color.BLUE;
				else if (connected == total)
					col = Color.GREEN;
				else if (connected > 0)
					col = Color.ORANGE;
				else if (total > 0)
					col = Color.RED;
			}
			var original = ImageIO.read(resource);
			var bim = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
			var graphics = (Graphics2D) bim.getGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics.drawImage(original, 0, 0, sz, sz, null);
			if (col != null) {
				graphics.setColor(col);
				graphics.fillOval(0, 0, sz / 3, sz / 3);
			}
			return bim;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load image " + resource + ".", e);
		}
	}

}
