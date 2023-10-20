package com.logonbox.vpn.client.gui.jfx;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.logonbox.vpn.client.common.api.Branding;
import com.logonbox.vpn.client.common.api.BrandingInfo;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.paint.Color;

public class Styling {
	static Logger log = LoggerFactory.getLogger(Styling.class);
	private UIContext context;

	public Styling(UIContext context) {
		this.context = context;
	}

	public void writeJavaFXCSS(Branding branding) {
		try {
			File tmpFile = getCustomJavaFXCSSFile();
			String url = toUri(tmpFile).toExternalForm();
			if (log.isDebugEnabled())
				log.debug(String.format("Writing JavafX style sheet to %s", url));
			PrintWriter pw = new PrintWriter(new FileOutputStream(tmpFile));
			try {
				pw.println(getCustomJavaFXCSSResource(branding));
			} finally {
				pw.close();
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not create custom CSS resource.");
		}
	}

	public File getCustomLocalWebCSSFile() {
		File tmpFile;
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			tmpFile = new File(context.getAppContext().getTempDir(), System.getProperty("user.name") + "-lbvpn-web.css");
		else
			tmpFile = new File(context.getAppContext().getTempDir(), "lbvpn-web.css");
		return tmpFile;
	}

	public Color getBase() {
		if (context.isDarkMode()) {
			if (SystemUtils.IS_OS_LINUX)
				return Color.valueOf("#1c1f22");
			else if (SystemUtils.IS_OS_MAC_OSX)
				return Color.valueOf("#231f25");
			else
				return Color.valueOf("#202020");
		} else
			return Color.WHITE;
	}

	public File getCustomJavaFXCSSFile() {
		if (System.getProperty("hypersocket.bootstrap.distDir") == null)
			return new File(context.getAppContext().getTempDir(),
					System.getProperty("user.name") + "-lbvpn-jfx.css");
		else
			return new File(context.getAppContext().getTempDir(),
					"lbvpn-jfx.css");
	}

	String getCustomJavaFXCSSResource(Branding branding) {
		StringBuilder bui = new StringBuilder();

		// Get the base colour. All other colours are derived from this
		Color backgroundColour = Color
				.valueOf(branding == null ? BrandingInfo.DEFAULT_BACKGROUND : branding.getResource().getBackground());
		Color foregroundColour = Color
				.valueOf(branding == null ? BrandingInfo.DEFAULT_FOREGROUND : branding.getResource().getForeground());

		if (backgroundColour.getOpacity() == 0) {
			// Prevent total opacity, as mouse events won't be received
			backgroundColour = new Color(backgroundColour.getRed(), backgroundColour.getGreen(),
					backgroundColour.getBlue(), 1f / 255f);
		}

		Color baseColor = getBase();
		Color linkColor;

		if(contrast(baseColor, backgroundColour) < 3) {
			/* Brightness is similar, use foreground as basic of link color */
			var b3 = foregroundColour.getBrightness();
			if(b3 > 0.5)
				linkColor = foregroundColour.deriveColor(0, 0, 0.75, 1.0);
			else
				linkColor = foregroundColour.deriveColor(0, 0, 1.25, 1.0);
		}
		else {
			/* Brightness is dissimilar, use foreground as basic of link color */
			linkColor = backgroundColour;
		}

		bui.append("* {\n");

		bui.append("-fx-lbvpn-background: ");
		bui.append(toHex(backgroundColour));
		bui.append(";\n");

		bui.append("-fx-lbvpn-foreground: ");
		bui.append(toHex(foregroundColour));
		bui.append(";\n");

		bui.append("-fx-lbvpn-base: ");
		bui.append(toHex(getBase()));
		bui.append(";\n");

		bui.append("-fx-lbvpn-base-inverse: ");
		bui.append(toHex(getBaseInverse()));
		bui.append(";\n");

		bui.append("-fx-lbvpn-link: ");
		bui.append(toHex(linkColor));
		bui.append(";\n");

//
		// Highlight
		if (backgroundColour.getSaturation() == 0) {
			// Greyscale, so just use HS blue
			bui.append("-fx-lbvpn-accent: 000033;\n");
			bui.append("-fx-lbvpn-accent2: 0e0041;\n");
		} else {
			// A colour, so choose the next adjacent colour in the HSB colour
			// wheel (45 degrees)
			bui.append("-fx-lbvpn-accent: " + toHex(backgroundColour.deriveColor(45f, 1f, 1f, 1f)) + ";\n");
			bui.append("-fx-lbvpn-accent: " + toHex(backgroundColour.deriveColor(-45f, 1f, 1f, 1f)) + ";\n");
		}

		// End
		bui.append("}\n");

		return bui.toString();

	}

	protected Color getBaseInverse() {
		if (context.isDarkMode())
			return Color.WHITE;
		else
			return Color.BLACK;
	}

	public void writeLocalWebCSS(Branding branding) {
		File tmpFile = getCustomLocalWebCSSFile();
		tmpFile.getParentFile().mkdirs();
		String url = toUri(tmpFile).toExternalForm();
		if (log.isDebugEnabled())
			log.debug(String.format("Writing local web style sheet to %s", url));
		String bgStr = branding == null ? BrandingInfo.DEFAULT_BACKGROUND : branding.getResource().getBackground();
		String fgStr = branding == null ? BrandingInfo.DEFAULT_FOREGROUND : branding.getResource().getForeground();
		Color fgColor = Color.valueOf(fgStr);
		Color bgColor = Color.valueOf(bgStr);
		Color baseColor = getBase();
		Color linkColor;

		if(contrast(baseColor, bgColor) < 3) {
			/* Brightness is similar, use foreground as basic of link color */
			var b3 = fgColor.getBrightness();
			if(b3 > 0.5)
				linkColor = fgColor.deriveColor(0, 0, 0.75, 1.0);
			else
				linkColor = fgColor.deriveColor(0, 0, 1.25, 1.0);
		}
		else {
			/* Brightness is dissimilar, use foreground as basic of link color */
			linkColor = bgColor;
		}

		String accent1Str = toHex(bgColor.deriveColor(0, 1, 0.85, 1));
		String accent2Str = toHex(bgColor.deriveColor(0, 1, 1.15, 1));
		String baseStr = toHex(baseColor);
		String baseInverseStr = toHex(getBaseInverse());
		String linkStr = toHex(linkColor);
		String baseInverseRgbStr = toRgba(getBaseInverse(), 0.05f);
		try (PrintWriter output = new PrintWriter(new FileWriter(tmpFile))) {
			try (BufferedReader input = new BufferedReader(new InputStreamReader(UI.class.getResource("local.css").openStream()))) {
				String line;
				while(( line = input.readLine()) != null) {
					line = line.replace("${lbvpnBackground}", bgStr);
					line = line.replace("${lbvpnForeground}", fgStr);
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

	static String toRgb(Color color) {
		return toRgba(color, -1);
	}

	static String toRgba(Color color, boolean opacity) {
		return toRgba(color, opacity ? color.getOpacity() : -1);
	}

	static String toRgba(Color color, double opacity) {
		if (opacity > -1)
			return String.format("rgba(%3f,%3f,%3f,%3f)", color.getRed(), color.getGreen(),
					color.getBlue(), opacity);
		else
			return String.format("rgba(%3f,%3f,%3f)", color.getRed(), color.getGreen(),
					color.getBlue());
	}

	public static URL toUri(File tmpFile) {
		try {
			return tmpFile.toURI().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

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

	static double lum(Color c) {
		List<Double> a = Arrays.asList(c.getRed(), c.getGreen(), c.getBlue()).stream()
				.map(v -> v <= 0.03925 ? v / 12.92f : Math.pow((v + 0.055f) / 1.055f, 2.4f)).collect(Collectors.toList());
		return a.get(0) * 0.2126 + a.get(1) * 0.7152 + a.get(2) * 0.0722;
	}

	static double contrast(Color c1, Color c2) {
		var brightest = Math.max(lum(c1), lum(c2));
	    var darkest = Math.min(lum(c1), lum(c2));
	    return (brightest + 0.05f)
	         / (darkest + 0.05f);
	}
}
