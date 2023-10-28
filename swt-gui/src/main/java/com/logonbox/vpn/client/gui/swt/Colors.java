package com.logonbox.vpn.client.gui.swt;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.logonbox.vpn.client.common.lbapi.Branding;
import com.logonbox.vpn.client.common.lbapi.BrandingInfo;

import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.RGBA;


public class Colors {

	private RGBA fgColor;
	private RGBA bgColor;
	private RGBA baseColor;
	private RGBA linkColor;

	public Colors(Branding branding, RGBA baseColor) {
		String bgStr = branding == null ? BrandingInfo.DEFAULT_BACKGROUND : branding.resource().background();
		String fgStr = branding == null ? BrandingInfo.DEFAULT_FOREGROUND : branding.resource().foreground();
		fgColor = Colors.valueOf(fgStr);
		bgColor = Colors.valueOf(bgStr);
		this.baseColor = baseColor;

		if (Colors.contrast(baseColor, bgColor) < 3) {
			/* Brightness is similar, use foreground as basic of link color */
			var b3 = fgColor.getHSBA()[2];
			if (b3 > 0.5f)
				linkColor = Colors.deriveRGBA(fgColor, 0.0f, 0.0f, 0.75f, 1.0f);
			else
				linkColor = Colors.deriveRGBA(fgColor, 0f, 0f, 1.25f, 1.0f);
		} else {
			/* Brightness is dissimilar, use foreground as basic of link color */
			linkColor = bgColor;
		}
	}

	public RGBA getFgColor() {
		return fgColor;
	}

	public void setFgColor(RGBA fgColor) {
		this.fgColor = fgColor;
	}

	public RGBA getBgColor() {
		return bgColor;
	}

	public void setBgColor(RGBA bgColor) {
		this.bgColor = bgColor;
	}

	public RGBA getBaseColor() {
		return baseColor;
	}

	public void setBaseColor(RGBA baseColor) {
		this.baseColor = baseColor;
	}

	public RGBA getLinkColor() {
		return linkColor;
	}

	public void setLinkColor(RGBA linkColor) {
		this.linkColor = linkColor;
	}

	static RGBA valueOf(String string) {
		while (string.startsWith("#"))
			string = string.substring(1);
		int r, g, b, a;
		if (string.length() == 3 || string.length() == 4) {
			r = Integer.parseInt(string.substring(0, 1), 16);
			g = Integer.parseInt(string.substring(1, 2), 16);
			b = Integer.parseInt(string.substring(2, 3), 16);
		} else if (string.length() == 6 || string.length() == 8) {
			r = Integer.parseInt(string.substring(0, 2), 16);
			g = Integer.parseInt(string.substring(2, 4), 16);
			b = Integer.parseInt(string.substring(4, 6), 16);
		} else
			throw new IllegalArgumentException(String.format("Failed to parse hex color %s.", string));

		if (string.length() == 4) {
			a = Integer.parseInt(string.substring(3, 4), 16);
		} else if (string.length() == 8) {
			a = Integer.parseInt(string.substring(6, 8), 16);
		} else
			a = 255;
		return new RGBA(r, g, b, a);
	}

	static RGBA deriveRGBA(RGBA original, float rf, float gf, float bf, float af) {
		return new RGBA((int) ((float) original.rgb.red * rf), (int) ((float) original.rgb.green * gf),
				(int) ((float) original.rgb.blue * bf), (int) ((float) original.alpha * af));
	}

	static String toHex(RGBA color) {
		return toHex(color.rgb, -1);
	}

	static String toHex(RGB color) {
		return toHex(color, -1);
	}

	static String toHex(RGBA color, boolean opacity) {
		return toHex(color.rgb, opacity ? color.alpha : -1);
	}

	static String toHex(RGB color, double opacity) {
		if (opacity > -1)
			return String.format("#%02x%02x%02x%02x", color.red, color.green, color.blue, (int) (opacity * 255.0));
		else
			return String.format("#%02x%02x%02x", color.red, color.green, color.blue);
	}

	static String toRgb(RGB color) {
		return toRgba(color, -1);
	}

	static String toRgb(RGBA color) {
		return toRgba(color.rgb, -1);
	}

	static String toRgba(RGBA color, boolean opacity) {
		return toRgba(color.rgb, opacity ? color.alpha : -1);
	}

	static String toRgba(RGB color, double opacity) {
		if (opacity > -1)
			return String.format("rgba(%3f,%3f,%3f,%3f)", (float) color.red / 255.0f, (float) color.green / 255.0f,
					(float) color.blue / 255.0f, opacity);
		else
			return String.format("rgba(%3f,%3f,%3f)", (float) color.red / 255.0f, (float) color.green / 255.0f,
					(float) color.blue / 255.0f);
	}

	static double lum(RGB c) {
		List<Double> a = Arrays.asList((float) c.red / 255.0f, (float) c.green / 255.0f, (float) c.blue / 255.0f)
				.stream().map(v -> v <= 0.03925 ? v / 12.92f : Math.pow((v + 0.055f) / 1.055f, 2.4f))
				.collect(Collectors.toList());
		return a.get(0) * 0.2126 + a.get(1) * 0.7152 + a.get(2) * 0.0722;
	}

	static double contrast(RGBA c1, RGBA c2) {
		return contrast(c1.rgb, c2.rgb);
	}

	static double contrast(RGB c1, RGB c2) {
		var brightest = Math.max(lum(c1), lum(c2));
		var darkest = Math.min(lum(c1), lum(c2));
		return (brightest + 0.05f) / (darkest + 0.05f);
	}

}
