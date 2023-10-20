package com.logonbox.vpn.client.attach;

import android.app.Activity;

import com.wireguard.android.backend.GoBackend;

import java.util.Set;

public class WgTunnelService {

    private final Activity activity;
    private GoBackend backend;

    public WgTunnelService(Activity activity) {
        this.activity = activity;
        backend = new GoBackend(activity);
    }

    public Set<String> getRunningTunnelNames() {
        return backend.getRunningTunnelNames();
    }
}
