package com.logonbox.vpn.client.tray;

import static com.sshtools.dobbin.IndicatorMenuItem.action;
import static com.sshtools.dobbin.IndicatorMenuItem.separator;

import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.TrayMode;
import com.logonbox.vpn.client.common.UiConfiguration;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.sshtools.dobbin.Indicator;
import com.sshtools.dobbin.IndicatorArea;
import com.sshtools.dobbin.IndicatorMenuItem;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DobbinTray extends AbstractTray {

    private final IndicatorArea area;
    private Indicator indicator;
    private String errorText = "";
    private boolean closed;

    public DobbinTray(TrayDaemon context) throws Exception {
        super(context);
        area = new IndicatorArea.Builder().
//                loop(t -> context.getScheduler().execute(t)).
                build();
    }

    @Override
    public boolean isActive() {
        return !closed;
    }

    @Override
    public void reload() {
        area.task(() -> {

            boolean connected;
            List<VpnConnection> conx;

            try {
                connected = context.getVpnManager().isBackendAvailable();
                conx = connected ? context.getVpnManager().getVpn().map(vpn -> Arrays.asList(vpn.getConnections()))
                        .orElse(Collections.emptyList()) : Collections.emptyList();

            } catch (Exception re) {
                conx = Collections.emptyList();
                connected = false;
            }

            if (indicator == null) {
                indicator = area.builder().menu(rebuildMenu(connected, conx)).build();
            } else {
                indicator.update(rebuildMenu(connected, conx));
            }

            indicator.tooltip(errorText);
            setImage(connected, conx);
        });
    }

    @Override
    public void loop() throws InterruptedException {
        while (!closed)
            Thread.sleep(1000);
    }

    @Override
    protected void onClose() throws Exception {
        indicator.close();
        area.close();
        closed = true;
    }

    @Override
    protected boolean isDefaultDark() {
        return false;
    }

    private void addDevice(VpnConnection device, List<IndicatorMenuItem> menuEntries, List<VpnConnection> devs)
            throws IOException {
        /* Open */
        Type status = Type.valueOf(device.getStatus());
        if (status == Type.CONNECTED) {
            menuEntries.add(action(bundle.getString("disconnect"),
                    item -> context.getScheduler().execute(() -> device.disconnect(""))));
        } else if (devs.size() > 0 && status == Type.DISCONNECTED) {
            menuEntries.add(action(bundle.getString("connect"),
                    item -> context.getScheduler().execute(() -> device.connect())));
        }
    }

    private List<IndicatorMenuItem> rebuildMenu(boolean connected, List<VpnConnection> devs) {
        var menuEntries = new ArrayList<IndicatorMenuItem>();
        menuEntries.add(action(bundle.getString("open"), item -> context.open()));
        menuEntries.add(separator());

        try {
            if (connected) {
                if (devs.size() == 1) {
                    addDevice(devs.get(0), menuEntries, devs);
                } else {
                    for (var dev : devs) {
                        var childEntries = new ArrayList<IndicatorMenuItem>();
                        addDevice(dev, childEntries, devs);
                        menuEntries.add(IndicatorMenuItem.submenu(dev.getDisplayName(),
                                childEntries.toArray(new IndicatorMenuItem[0])));
                    }
                }
            }
        } catch (Exception e) {
            errorText = e.getLocalizedMessage() == null ? "Error" : e.getLocalizedMessage();
        }

        menuEntries.add(action(bundle.getString("options"), item -> context.options()));
        menuEntries.add(action(bundle.getString("quit"), item -> context.confirmExit()));

        return menuEntries;
    }

    private void setImage(boolean connected, List<VpnConnection> devs) {
        try {
            if (context.getVpnManager().isBackendAvailable()) {
                TrayMode icon = UiConfiguration.get().getValue(null, UiConfiguration.TRAY_MODE);
                if (TrayMode.LIGHT.equals(icon)) {
                    indicator.icon(locateImage("light-logonbox-icon", 64, devs));
                } else if (TrayMode.DARK.equals(icon)) {
                    indicator.icon(locateImage("dark-logonbox-icon", 64, devs));
                } else if (TrayMode.COLOR.equals(icon)) {
                    indicator.icon(locateImage("color-logonbox-icon", 64, devs));
                } else {
                    if (isDark())
                        indicator.icon(locateImage("light-logonbox-icon", 64, devs));
                    else
                        indicator.icon(locateImage("dark-logonbox-icon", 64, devs));
                }
            } else {
                throw new IOException("No backend.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            indicator.icon(SWTTray.class.getResource("error-64x64.png"));
        }
    }

    protected URL locateImage(String resource, int sz, List<VpnConnection> devs) {

        resource += sz + "x" + sz;

        int connecting = 0;
        int connected = 0;
        int authorizing = 0;
        int total = 0;
        for (var s : devs) {
            Type status = Type.valueOf(s.getStatus());
            if (status == Type.CONNECTED)
                connected++;
            else if (status == Type.AUTHORIZING)
                authorizing++;
            else if (status == Type.CONNECTING)
                connecting++;
            total++;
        }
        if (total > 0) {
            if (authorizing > 0)
                resource += "-blue";
            else if (connecting > 0)
                resource += "-blue";
            else if (connected == total)
                resource += "-green";
            else if (connected > 0)
                resource += "-dark-green";
            else if (total > 0)
                resource += "-red";
        }

        resource += ".png";

        return SWTTray.class.getResource(resource);
    }
}
