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
package com.logonbox.vpn.client.gui.jfx;

import com.logonbox.vpn.client.common.DarkMode;
import com.logonbox.vpn.client.common.TrayMode;
import com.logonbox.vpn.client.common.UiConfiguration;
import com.sshtools.jini.INI.Section;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public class JavaFXUiConfiguration {

	private ObjectProperty<TrayMode> trayMode = new SimpleObjectProperty<TrayMode>();
    private ObjectProperty<DarkMode> darkMode = new SimpleObjectProperty<DarkMode>();
	private StringProperty logLevel = new SimpleStringProperty();
	private StringProperty configurationFileDirectory = new SimpleStringProperty();
	private IntegerProperty w = new SimpleIntegerProperty();
	private IntegerProperty h = new SimpleIntegerProperty();
	private IntegerProperty x = new SimpleIntegerProperty();
	private IntegerProperty y = new SimpleIntegerProperty();

	private final static JavaFXUiConfiguration DEFAULT_INSTANCE = new JavaFXUiConfiguration(UiConfiguration.get());

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
	
	public JavaFXUiConfiguration(UiConfiguration uiConfig) {
		
        var wnd = uiConfig.window();
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

        var ui = uiConfig.ui();
		trayMode.set(ui.getEnum(TrayMode.class, "trayMode", TrayMode.AUTO)); 
		trayMode.addListener(new EnumPreferenceUpdateChangeListener<TrayMode>(ui, "trayMode"));

		darkMode.set(ui.getEnum(DarkMode.class, "darkMode", DarkMode.AUTO));
		darkMode.addListener(new EnumPreferenceUpdateChangeListener<DarkMode>(ui, "darkMode"));

        var filesystem = uiConfig.filesystem();

		configurationFileDirectory.set(filesystem.get("configurationFileDirectory", System.getProperty("user.home")));
		configurationFileDirectory.addListener(new StringPreferenceUpdateChangeListener(filesystem, "configurationFileDirectory"));

        var troubleshooting = uiConfig.troubleshooting();
		logLevel.set(troubleshooting.get("logLevel", null));
		logLevel.addListener(new StringPreferenceUpdateChangeListener(troubleshooting, "logLevel"));

	}

	public static JavaFXUiConfiguration getDefault() {
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

	public ObjectProperty<DarkMode> darkModeProperty() {
		return darkMode;
	}

	public StringProperty logLevelProperty() {
		return logLevel;
	}
}
