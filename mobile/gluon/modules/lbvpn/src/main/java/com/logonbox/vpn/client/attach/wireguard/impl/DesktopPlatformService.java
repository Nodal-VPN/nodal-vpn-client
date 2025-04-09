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
import com.logonbox.vpn.drivers.lib.DNSProvider;
import com.logonbox.vpn.drivers.lib.PlatformService;
import com.logonbox.vpn.drivers.lib.StartRequest;
import com.logonbox.vpn.drivers.lib.SystemContext;
import com.logonbox.vpn.drivers.lib.VpnAdapter;
import com.logonbox.vpn.drivers.lib.VpnAdapterConfiguration;
import com.logonbox.vpn.drivers.lib.VpnAddress;
import com.logonbox.vpn.drivers.lib.VpnConfiguration;
import com.logonbox.vpn.drivers.lib.VpnInterfaceInformation;
import com.logonbox.vpn.drivers.lib.VpnPeer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Desktop implementation of a {@link PlatformService}, responsible for querying
 * the state of existing VPN connections, creating new ones and other platform
 * specific actions. Delegates to the same modules used in the full desktop
 * VPN client build.
 */
public class DesktopPlatformService implements MobilePlatformService<VpnAddress> {

    /**
     * Constructor
     */
    private final PlatformService<?> delegate;

    /**
     * Constructor
     */
    public DesktopPlatformService(PlatformService<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void openToEveryone(Path path) throws IOException {
        delegate.openToEveryone(path);
    }

    @Override
    public void restrictToUser(Path path) throws IOException {
        delegate.restrictToUser(path);        
    }

    @Override
    public VpnAdapter adapter(String nativeName) {
        return delegate.adapter(nativeName);
    }

    @Override
    public List<VpnAdapter> adapters() {
        return delegate.adapters();
    }

    @Override
    public VpnAddress address(String nativeName) {
        return delegate.address(nativeName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<VpnAddress> addresses() {
        return (List<VpnAddress>) delegate.addresses();
    }

    @Override
    public void append(VpnAdapter adapter, VpnAdapterConfiguration config) throws IOException {
       delegate.append(adapter, config); 
    }

    @Override
    public VpnAdapterConfiguration configuration(VpnAdapter adapter) {
        return delegate.configuration(adapter);
    }

    @Override
    public SystemContext context() {
        return delegate.context();
    }

    @Override
    public Optional<VpnPeer> defaultGateway() {
        return delegate.defaultGateway();
    }

    @Override
    public void defaultGateway(VpnPeer peer) throws IOException {
        delegate.defaultGateway(peer);
    }

    @Override
    public Optional<DNSProvider> dns() {
        return delegate.dns();
    }

    @Override
    public VpnInterfaceInformation information(VpnAdapter adapter) {
        return delegate.information(adapter);
    }

    @Override
    public void reconfigure(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
        delegate.reconfigure(adapter, configuration);
    }

    @Override
    public void remove(VpnAdapter adapter, String publicKey) throws IOException {
        delegate.remove(adapter, publicKey);
    }

    @Override
    public void resetDefaulGateway() throws IOException {
        delegate.resetDefaulGateway();        
    }

    @Override
    public void runHook(VpnConfiguration configuration, VpnAdapter adapter, String... args) throws IOException {
        delegate.runHook(configuration, adapter, args);
    }

    @Override
    public void stop(VpnConfiguration configuration, VpnAdapter adapter) throws IOException {
        delegate.stop(configuration, adapter);
    }

    @Override
    public void sync(VpnAdapter configuration, VpnAdapterConfiguration adapter) throws IOException {
        delegate.sync(configuration, adapter);
    }

    @Override
    public Optional<String> interfaceNameToNativeName(String ifaceName) {
        return delegate.interfaceNameToNativeName(ifaceName);
    }

    @Override
    public boolean isValidNativeInterfaceName(String nativeName) {
        return delegate.isValidNativeInterfaceName(nativeName);
    }

    @Override
    public Optional<String> nativeNameToInterfaceName(String nativeName) {
        return delegate.nativeNameToInterfaceName(nativeName);
    }

    @Override
    public VpnAdapter start(StartRequest req) throws IOException {
        return delegate.start(req);
    }

    @Override
    public boolean adapterExists(String nativeName) {
        return delegate.adapterExists(nativeName);
    }

    @Override
    public boolean addressExists(String nativeName) {
        return delegate.addressExists(nativeName);
    }

    @Override
    public Optional<VpnAdapter> getByPublicKey(String publicKey) throws IOException {
        return delegate.getByPublicKey(publicKey);
    }

    @Override
    public Instant getLatestHandshake(VpnAddress address, String publicKey) throws IOException {
        return delegate.getLatestHandshake(address, publicKey);
    }


    
}
