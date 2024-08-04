package com.logonbox.vpn.client.gui.jfx;

import com.logonbox.vpn.client.common.AppConstants;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.lbapi.Branding;
import com.logonbox.vpn.client.common.lbapi.BrandingInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.paint.Color;

public class Styling {
	static Logger log = LoggerFactory.getLogger(Styling.class);
	private UIContext<?> context;

	public Styling(UIContext<?> context) {
		this.context = context;
	}

	public void apply(Branding branding) {
	    writeJavaFXCSS(branding);
	    writeLocalWebCSS(branding);
	}

	public Path getCustomLocalWebCSSFile() {
        if(AppVersion.isDeveloperWorkspace()) {
            return context.getAppContext().getTempDir().resolve(System.getProperty("user.name") + "-lbvpn-web.css");
        }
        else {
            return AppConstants.CLIENT_HOME.resolve("lbvpn-web.css");
        }
	}

	public Color getBase() {
		if (context.isDarkMode()) {
		    return Color.valueOf(PlatformUtilities.get().getApproxDarkModeColor());
		} else
			return Color.WHITE;
	}

	public Path getCustomJavaFXCSSFile() {
	    if(AppVersion.isDeveloperWorkspace()) {
	        return context.getAppContext().getTempDir().resolve(System.getProperty("user.name") + "-lbvpn-jfx.css");
	    }
	    else {
            return AppConstants.CLIENT_HOME.resolve("lbvpn-jfx.css");
	    }
	}
	
	private void writeJavaFXCSS(Branding branding) {
        try {
            var tmpFile = getCustomJavaFXCSSFile();
            var url = toUri(tmpFile).toExternalForm();
            if (log.isDebugEnabled())
                log.debug(String.format("Writing JavafX style sheet to %s", url));
            Files.createDirectories(tmpFile.getParent());
            var pw = new PrintWriter(Files.newOutputStream(tmpFile));
            try {
                pw.println(getCustomJavaFXCSSResource(branding));
            } finally {
                pw.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create custom CSS resource.", e);
        }
    }


	String getCustomJavaFXCSSResource(Branding branding) {
		StringBuilder bui = new StringBuilder();

		// Get the base colour. All other colours are derived from this
        Color backgroundColour = safeParseColor(branding == null ? null : branding.resource().background(), BrandingInfo.DEFAULT_BACKGROUND);
        Color foregroundColour = safeParseColor(branding == null ? null : branding.resource().foreground(), BrandingInfo.DEFAULT_FOREGROUND);
		
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
			// Greyscale, so just use JAD very dark grey
			bui.append("-fx-lbvpn-accent: 0d0d0d;\n");
			bui.append("-fx-lbvpn-accent2: 0e0041;\n");
		} else {
			// A colour, so choose the next adjacent colour in the HSB colour
			// wheel (45 degrees)
			bui.append("-fx-lbvpn-accent: " + toHex(backgroundColour.deriveColor(45f, 1f, 1f, 1f)) + ";\n");
			bui.append("-fx-lbvpn-accent2: " + toHex(backgroundColour.deriveColor(-45f, 1f, 1f, 1f)) + ";\n");
		}

		// End
		bui.append("}\n");
		
		return bui.toString();

	}

    String checkFAUrl(String res) {
        var url = Styling.class.getResource("fontawesome/fonts/" + res);
        if(url == null) {
            return "";
        }
        else
            return url.toExternalForm();
    }

	protected Color getBaseInverse() {
		if (context.isDarkMode())
			return Color.WHITE;
		else
			return Color.BLACK;
	}

	private void writeLocalWebCSS(Branding branding) {
		var tmpFile = getCustomLocalWebCSSFile();
		try {
		    Files.createDirectories(tmpFile.getParent());
		}
		catch(IOException ioe) {}
        
		var url = toUri(tmpFile).toExternalForm();
		
		if (log.isDebugEnabled())
			log.debug(String.format("Writing local web style sheet to %s", url));
		
		var bgColor = safeParseColor(branding == null ? BrandingInfo.DEFAULT_BACKGROUND : branding.resource().background(), BrandingInfo.DEFAULT_BACKGROUND);
		var fgColor = safeParseColor(branding == null ? BrandingInfo.DEFAULT_FOREGROUND : branding.resource().foreground(), BrandingInfo.DEFAULT_FOREGROUND);
		var baseColor = getBase();
		
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

		var accent1Str = toHex(bgColor.deriveColor(0, 1, 0.85, 1));
		var accent2Str = toHex(bgColor.deriveColor(0, 1, 1.15, 1));
		var baseStr = toHex(baseColor);
		var baseInverseStr = toHex(getBaseInverse());
		var linkStr = toHex(linkColor);
		var baseInverseRgbStr = toRgba(getBaseInverse(), 0.05f);
		try (var output = new PrintWriter(Files.newOutputStream(tmpFile))) {
			
//			/* Strangeness on Windows - https://stackoverflow.com/questions/42416242/java-fx-failed-to-load-font-awesome-icons */
//			output.write("""
//			@font-face {
//			    font-family: 'FontAwesome';
//			    src: url('%url1%');
//			    src: url('%url1%') format('embedded-opentype'), url('%url2%') format('woff2'), url('%url3%') format('woff'), url('%url4%') format('truetype'), url('%url5%') format('svg');
//			    font-weight: normal;
//			    font-style: normal;
//			  }
//			""".
//			    replace("%url1%", checkFAUrl("fontawesome-webfont.eot")).
//			    replace("%url2%", checkFAUrl("fontawesome-webfont.woff2")).
//	            replace("%url3%", checkFAUrl("fontawesome-webfont.woff")).
//	            replace("%url4%", checkFAUrl("fontawesome-webfont.ttf")).
//	            replace("%url5%", checkFAUrl("fontawesome-webfont.svg"))
//			);
			
			
			try (var input = new BufferedReader(new InputStreamReader(UI.class.getResource("local.css").openStream()))) {
				String line;
				while(( line = input.readLine()) != null) {
					line = line.replace("${lbvpnBackground}", toHex(bgColor));
					line = line.replace("${lbvpnForeground}", toHex(fgColor));
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

	public static URL toUri(Path tmpFile) {
		try {
			return tmpFile.toUri().toURL();
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

    private static Color safeParseColor(String fgStr, String defaultColor) {
        try {
            return Color.valueOf(fgStr);
        }
        catch(Exception e) {
            return Color.valueOf(defaultColor);
        }
    }
}
