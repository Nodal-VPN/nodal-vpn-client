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
package com.logonbox.vpn.client.attach.wireguard.impl;

import com.logonbox.vpn.client.attach.wireguard.MobilePlatformService;
import com.logonbox.vpn.drivers.lib.AbstractPlatformService;
import com.logonbox.vpn.drivers.lib.PlatformService;
import com.logonbox.vpn.drivers.lib.StartRequest;
import com.logonbox.vpn.drivers.lib.SystemContext;
import com.logonbox.vpn.drivers.lib.VpnAdapter;
import com.logonbox.vpn.drivers.lib.VpnAdapterConfiguration;
import com.logonbox.vpn.drivers.lib.VpnConfiguration;
import com.logonbox.vpn.drivers.lib.VpnInterfaceInformation;
import com.logonbox.vpn.drivers.lib.VpnPeer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * IOS implementation of a {@link PlatformService}, responsible for querying the
 * state of existing VPN connections, creating new ones and other platform
 * specific actions.
 */
public class IOSPlatformService extends AbstractPlatformService<IOSIP> implements MobilePlatformService<IOSIP> {

    /**
     * Constructor
     */
    protected IOSPlatformService(SystemContext context) {
        super("wg", context);
    }

    @Override
    public void openToEveryone(Path path) throws IOException {
    }

    @Override
    public void restrictToUser(Path path) throws IOException {
    }

    private native Set<String> getRunningTunnelNames();

    @Override
    public List<VpnAdapter> adapters() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<IOSIP> addresses() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void append(VpnAdapter arg0, VpnAdapterConfiguration arg1) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public VpnAdapterConfiguration configuration(VpnAdapter arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VpnInterfaceInformation information(VpnAdapter arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reconfigure(VpnAdapter arg0, VpnAdapterConfiguration arg1) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(VpnAdapter arg0, String arg1) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void runHook(VpnConfiguration arg0, VpnAdapter arg1, String... arg2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sync(VpnAdapter arg0, VpnAdapterConfiguration arg1) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void onResetDefaultGateway(VpnPeer arg0) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void onSetDefaultGateway(VpnPeer arg0) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public VpnAdapter start(StartRequest arg0) throws IOException {
        throw new UnsupportedOperationException();
    }

}
