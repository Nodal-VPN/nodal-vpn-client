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
package com.logonbox.vpn.client.embedded;

import com.logonbox.vpn.client.common.AbstractVpnManager;
import com.logonbox.vpn.client.common.api.IRemoteUI;
import com.logonbox.vpn.client.common.api.IVpn;

import java.util.Optional;

public class EmbeddedVpnManager extends AbstractVpnManager<EmbeddedVpnConnection> {
    
    private EmbeddedService service;

    EmbeddedVpnManager(EmbeddedService service) {
        this.service = service;
    }
    
    @Override
    public boolean isBackendAvailable() {
        return true;
    }

    @Override
    public Optional<IVpn<EmbeddedVpnConnection>> getVpn() {
        return service.getVpn();
    }

    @Override
    public void confirmExit() {
    }

    @Override
    public Optional<IRemoteUI> getUserInterface() {
        return Optional.empty();
    }

    @Override
    public void start() {        
    }

}
