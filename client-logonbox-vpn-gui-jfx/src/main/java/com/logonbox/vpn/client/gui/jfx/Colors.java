package com.logonbox.vpn.client.gui.jfx;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.paint.Color;

public class Colors {

	static String toHex(Color color) {
		return toHex(color, -1);
	}

	static String toHex(Color color, boolean opacity) {
		return toHex(color, opacity ? color.getOpacity() : -1);
	}

	static String toHex(Color color, double opacity) {
		if (opacity > -1)
			return String.format("#%02x%02x%02x%02x", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
					(int) (color.getBlue() * 255), (int) (opacity * 255));
		else
			return String.format("#%02x%02x%02x", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
					(int) (color.getBlue() * 255));
	}

	static String toRgb(Color color) {
		return toRgba(color, -1);
	}

	static String toRgba(Color color, boolean opacity) {
		return toRgba(color, opacity ? color.getOpacity() : -1);
	}

	static String toRgba(Color color, double opacity) {
		if (opacity > -1)
			return String.format("rgba(%3f,%3f,%3f,%3f)", color.getRed(), color.getGreen(), color.getBlue(), opacity);
		else
			return String.format("rgba(%3f,%3f,%3f)", color.getRed(), color.getGreen(), color.getBlue());
	}

	static double lum(Color c) {
		List<Double> a = Arrays.asList(c.getRed(), c.getGreen(), c.getBlue()).stream()
				.map(v -> v <= 0.03925 ? v / 12.92f : Math.pow((v + 0.055f) / 1.055f, 2.4f))
				.collect(Collectors.toList());
		return a.get(0) * 0.2126 + a.get(1) * 0.7152 + a.get(2) * 0.0722;
	}

	static double contrast(Color c1, Color c2) {
		var brightest = Math.max(lum(c1), lum(c2));
		var darkest = Math.min(lum(c1), lum(c2));
		return (brightest + 0.05f) / (darkest + 0.05f);
	}

}
