package com.logonbox.vpn.client.gui.jfx;

import com.logonbox.vpn.client.common.AppConstants;
import com.logonbox.vpn.client.common.ConfigurationItem.TrayMode;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public class Configuration {
	
	public static final String DARK_MODE_AUTO = "auto";
	public static final String DARK_MODE_ALWAYS = "always";
	public static final String DARK_MODE_NEVER = "never";

	private ObjectProperty<TrayMode> trayMode = new SimpleObjectProperty<TrayMode>();
	private StringProperty darkMode = new SimpleStringProperty();
	private StringProperty logLevel = new SimpleStringProperty();
	private StringProperty configurationFileDirectory = new SimpleStringProperty();
	private IntegerProperty w = new SimpleIntegerProperty();
	private IntegerProperty h = new SimpleIntegerProperty();
	private IntegerProperty x = new SimpleIntegerProperty();
	private IntegerProperty y = new SimpleIntegerProperty();

	//
	private final static Configuration DEFAULT_INSTANCE = new Configuration(
			new INISet.Builder(AppConstants.CLIENT_NAME).
			    withMonitor(new Monitor()).
			    withApp(AppConstants.CLIENT_NAME).
			    withoutSystemPropertyOverrides().
			    withWriteScope(Scope.USER).
			    build());

	class IntegerPreferenceUpdateChangeListener implements ChangeListener<Number> {

		private Section node;
		private String key;

		IntegerPreferenceUpdateChangeListener(Section node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
			node.put(key, newValue.intValue());
		}

	}

	class BooleanPreferenceUpdateChangeListener implements ChangeListener<Boolean> {

		private Section node;
		private String key;

		BooleanPreferenceUpdateChangeListener(Section node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
			node.put(key, newValue);
		}

	}

	class StringPreferenceUpdateChangeListener implements ChangeListener<String> {

		private Section node;
		private String key;

		StringPreferenceUpdateChangeListener(Section node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
			node.put(key, newValue);
		}

	}

    class EnumPreferenceUpdateChangeListener<E extends Enum<E>> implements ChangeListener<E> {

        private Section node;
        private String key;

        EnumPreferenceUpdateChangeListener(Section node, String key) {
            this.node = node;
            this.key = key;
        }

        @Override
        public void changed(ObservableValue<? extends E> observable, E oldValue, E newValue) {
            node.putEnum(key, newValue);
        }

    }
	
	public Configuration(INISet set) {
		var doc = set.document();
		
        var wnd = doc.obtainSection("window");
        x.set(wnd.getInt("x", 0));
		x.addListener((c, o, n) -> {
			wnd.put("x", n.intValue());
		});
		y.set(wnd.getInt("y", 0));
		y.addListener((c, o, n) -> {
			wnd.put("y", n.intValue());
		});
		w.set(wnd.getInt("w", 0));
		w.addListener((c, o, n) -> {
			wnd.put("w", n.intValue());
		});
		h.set(wnd.getInt("h", 0));
		h.addListener((c, o, n) -> {
			wnd.put("h", n.intValue());
		});

        var ui = doc.obtainSection("ui");
		trayMode.set(ui.getEnum(TrayMode.class, "trayMode", TrayMode.AUTO)); 
		trayMode.addListener(new EnumPreferenceUpdateChangeListener<TrayMode>(ui, "trayMode"));

		darkMode.set(ui.get("darkMode", DARK_MODE_AUTO));
		darkMode.addListener(new StringPreferenceUpdateChangeListener(ui, "darkMode"));

        var filesystem = doc.obtainSection("filesystem");

		configurationFileDirectory.set(filesystem.get("configurationFileDirectory", System.getProperty("user.home")));
		configurationFileDirectory.addListener(new StringPreferenceUpdateChangeListener(filesystem, "configurationFileDirectory"));

        var troubleshooting = doc.obtainSection("troubleshooting");
		logLevel.set(troubleshooting.get("logLevel", null));
		logLevel.addListener(new StringPreferenceUpdateChangeListener(troubleshooting, "logLevel"));

	}

	public static Configuration getDefault() {
		return DEFAULT_INSTANCE;
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

	public ObjectProperty<TrayMode> trayModeProperty() {
		return trayMode;
	}

	public StringProperty darkModeProperty() {
		return darkMode;
	}

	public StringProperty logLevelProperty() {
		return logLevel;
	}
}
