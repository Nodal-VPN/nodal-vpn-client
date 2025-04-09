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
package com.logonbox.vpn.client.common;

import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;

import java.nio.file.Path;

public class UiConfiguration implements ConfigurationRepository {
    public final static ConfigurationItem<TrayMode> TRAY_MODE = ConfigurationItem.add("trayMode", TrayMode.class, com.logonbox.vpn.client.common.ConfigurationItem.Scope.USER, TrayMode.AUTO, TrayMode.values());

    private final static class Default {
        private final static  UiConfiguration INSTANCE = new UiConfiguration(new INISet.Builder(AppConstants.CLIENT_NAME).
                withMonitor(new Monitor()).
                withApp(AppConstants.CLIENT_NAME).
                withoutSystemPropertyOverrides().
                withScopes(Scope.USER).
                withWriteScope(Scope.USER).
                build()); 
    }
    
    public static UiConfiguration get() {
        return Default.INSTANCE;
    }

    private INISet set;
    
    private UiConfiguration(INISet set) {
        this.set = set;
    }

    @Override
    public <V> V getValue(String owner, ConfigurationItem<V> item) {
        var ui = ui();
        var strVal = ui.get(item.getKey(), item.getDefaultValue().toString());
        return item.parse(strVal);
    }

    @Override
    public <V> void setValue(String owner, ConfigurationItem<V> item, V value) {
        ui().put(owner, value.toString());
    }

    public Section ui() {
        return set.document().obtainSection("ui");
    }
    
    public Section window() {
        return set.document().obtainSection("window");
    }

    public Section filesystem() {
        return set.document().obtainSection("filesystem");
    }

    public Section troubleshooting() {
        return set.document().obtainSection("troubleshooting");
    }

    @Override
    public Path getDirectory() {
        throw new UnsupportedOperationException();
    }
}
