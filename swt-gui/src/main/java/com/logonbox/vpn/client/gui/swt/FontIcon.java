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
package com.logonbox.vpn.client.gui.swt;

import static java.util.Objects.requireNonNull;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.IkonHandler;

import java.util.Arrays;

/**
 */
public class FontIcon extends Canvas {
	private static final Object LOCK = new Object[0];

	public static FontIcon control(Composite parent, Ikon ikon) {
		return control(parent, ikon, 8, parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
	}

	public static FontIcon control(Composite parent, Ikon ikon, Color iconColor) {
		return control(parent, ikon, 8, iconColor);
	}

	public static FontIcon control(Composite parent, Ikon ikon, int iconSize) {
		return control(parent, ikon, iconSize, parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
	}

	public static FontIcon control(Composite parent, Ikon ikon, int iconSize, Color iconColor) {
		var icon = new FontIcon(parent, SWT.NONE);
		icon.setIkon(ikon);
		icon.setIconSize(iconSize);
		icon.setIconColor(iconColor);
		return icon;
	}

	public static Image image(Device device, Ikon ikon) {
		return image(device, ikon, 8, device.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
	}

	public static Image image(Device device, Ikon ikon, Color iconColor) {
		return image(device, ikon, 8, iconColor);
	}

	public static Image image(Device device, Ikon ikon, int iconSize) {
		return image(device, ikon, iconSize, device.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
	}

	public static Image image(Device device, Ikon ikon, int iconSize, Color iconColor) {
		var img = createTransparentImage(device, iconSize, iconSize);
		var fontData = createFontData(ikon, iconSize);
		var font = new Font(device, fontData);
		var gc = new GC(img);
		paintIcon(ikon, gc, font, iconColor, null, iconSize);
		gc.dispose();
		font.dispose();
		return img;
	}

	protected static FontData createFontData(Ikon ikon, int iconSize) {
		IkonHandler ikonHandler = IkonResolver.getInstance().resolve(ikon.getDescription());
		return new FontData(ikonHandler.getFontFamily(), (int) ((float) iconSize * 0.65f), SWT.NORMAL);
	}

	protected static void paintIcon(Ikon ikon, GC g2, Font font, Color color, Color bg, int sz) {

		g2.setAntialias(SWT.ON);
		g2.setInterpolation(SWT.HIGH);

		if (bg != null) {
			g2.setBackground(bg);
			g2.fillRectangle(0, 0, sz, sz);
		}

		g2.setFont(font);
		g2.setForeground(color);

		String sym;  
		int code = ikon.getCode();
		if (code <= '\uFFFF') {
			sym = String.valueOf((char) code);
		} else {
			char[] charPair = Character.toChars(code);
			sym = new String(charPair);
		}
		Point w = g2.stringExtent(sym);
		int sx = (int)(( (float)sz - (float)w.x ) / 2.0f);
		int sy = (int)(( (float)sz - (float)w.y ) / 2.0f);

		g2.drawText(sym, sx, sy, true);
	}

	static Image createTransparentImage(Device display, int width, int height) {
		var d = new ImageData(width, height, 24, new PaletteData(0xff0000, 0x00ff00, 0x0000ff));
		d.setAlpha(0, 0, 0);
		Arrays.fill(d.alphaData, (byte) 0);
		return new Image(display, d);
	}

	private Font font;
	private FontData fontData;
	private Color iconColor;
	private int iconSize = 8;
	private Ikon ikon;

	public FontIcon(Composite parent) {
		this(parent, SWT.NONE);
	}

	public FontIcon(Composite parent, int style) {
		super(parent, style);
		addPaintListener(this::paintControl);
		addDisposeListener(this::disposeOthers);
	}

	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		return new Point(iconSize, iconSize);
	}

	public Color getIconColor() {
		return iconColor;
	}

	public int getIconSize() {
		return iconSize;
	}

	public Ikon getIkon() {
		return ikon;
	}

	public void setIconColor(Color iconColor) {
		requireNonNull(iconColor, "Argument 'iconColor' must not be null");
		this.iconColor = iconColor;
	}

	public void setIconSize(int iconSize) {
		if (iconSize > 0) {
			this.iconSize = iconSize;
			if (null != font) {
				fontData = createFontData(ikon, iconSize);
				disposeFont();
				font = new Font(getParent().getDisplay(), fontData);
			}
		}
	}

	public void setIkon(Ikon ikon) {
		this.ikon = ikon;
		synchronized (LOCK) {
			disposeFont();
			fontData = createFontData(ikon, iconSize);
			font = new Font(getParent().getDisplay(), fontData);
		}
	}

	void disposeFont() {
		if (font != null) {
			font.dispose();
			font = null;
		}
	}

	void disposeOthers(DisposeEvent e) {
		disposeFont();
	}

	void paintControl(PaintEvent e) {
		paintIcon(ikon, e.gc, font, iconColor, getBackground(), iconSize);
	}
}
