package com.logonbox.vpn.client.common;

import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;

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
}
