package com.logonbox.vpn.client.gui.jfx;

import java.util.prefs.Preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public class Configuration {
	public static final String TRAY_MODE = "trayMode";
	public static final String TRAY_MODE_DARK = "dark";
	public static final String TRAY_MODE_COLOR = "color";
	public static final String TRAY_MODE_LIGHT = "light";
	public static final String TRAY_MODE_AUTO = "auto";
	public static final String TRAY_MODE_OFF = "off";
	
	public static final String DARK_MODE_AUTO = "auto";
	public static final String DARK_MODE_ALWAYS = "always";
	public static final String DARK_MODE_NEVER = "never";

	private StringProperty darkMode = new SimpleStringProperty();
	private StringProperty logLevel = new SimpleStringProperty();
	private StringProperty configurationFileDirectory = new SimpleStringProperty();
	private IntegerProperty w = new SimpleIntegerProperty();
	private IntegerProperty h = new SimpleIntegerProperty();
	private IntegerProperty x = new SimpleIntegerProperty();
	private IntegerProperty y = new SimpleIntegerProperty();
	private BooleanProperty saveCookies = new SimpleBooleanProperty();

	//
	private final static Configuration DEFAULT_INSTANCE = new Configuration(
			Preferences.userNodeForPackage(Configuration.class));

	class IntegerPreferenceUpdateChangeListener implements ChangeListener<Number> {

		private Preferences node;
		private String key;

		IntegerPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
			node.putInt(key, newValue.intValue());
		}

	}

	class BooleanPreferenceUpdateChangeListener implements ChangeListener<Boolean> {

		private Preferences node;
		private String key;

		BooleanPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
			node.putBoolean(key, newValue);
		}

	}

	class StringPreferenceUpdateChangeListener implements ChangeListener<String> {

		private Preferences node;
		private String key;

		StringPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
			node.put(key, newValue);
		}

	}

	public Configuration(Preferences node) {
		x.set(node.getInt("x", 0));
		x.addListener((c, o, n) -> {
			node.putInt("x", n.intValue());
		});
		y.set(node.getInt("y", 0));
		y.addListener((c, o, n) -> {
			node.putInt("y", n.intValue());
		});
		w.set(node.getInt("w", 0));
		w.addListener((c, o, n) -> {
			node.putInt("w", n.intValue());
		});
		h.set(node.getInt("h", 0));
		h.addListener((c, o, n) -> {
			node.putInt("h", n.intValue());
		});

		darkMode.set(node.get("darkMode", DARK_MODE_AUTO));
		darkMode.addListener(new StringPreferenceUpdateChangeListener(node, "darkMode"));

		configurationFileDirectory.set(node.get("configurationFileDirectory", System.getProperty("user.home")));
		configurationFileDirectory.addListener(new StringPreferenceUpdateChangeListener(node, "configurationFileDirectory"));

		logLevel.set(node.get("logLevel", null));
		logLevel.addListener(new StringPreferenceUpdateChangeListener(node, "logLevel"));

		saveCookies.set(node.getBoolean("saveCookies", false));
		saveCookies
				.addListener(new BooleanPreferenceUpdateChangeListener(node, "saveCookies"));

		
	}

	public static Configuration getDefault() {
		return DEFAULT_INSTANCE;
	}
	
	public BooleanProperty saveCookiesProperty() {
		return saveCookies;
	}

	public StringProperty configurationFileProperty() {
		return configurationFileDirectory;
	}

	public IntegerProperty wProperty() {
		return w;
	}

	public IntegerProperty hProperty() {
		return h;
	}

	public IntegerProperty xProperty() {
		return x;
	}

	public IntegerProperty yProperty() {
		return y;
	}

	public StringProperty darkModeProperty() {
		return darkMode;
	}

	public StringProperty logLevelProperty() {
		return logLevel;
	}
}
