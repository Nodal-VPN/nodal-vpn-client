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

import com.jadaptive.nodal.core.lib.PlatformServiceFactory;
import com.logonbox.vpn.client.common.Api;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVpn;

import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.function.Predicate;

import uk.co.bithatch.nativeimage.annotations.Resource;

@Resource
public class EmbedderApi implements Api<EmbeddedVpnConnection> {

    public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(EmbedderApi.class.getName());
    private final EmbeddedService srv;
    
    public EmbedderApi() throws Exception {
        this(pf -> true);
    }
    
    public EmbedderApi(Predicate<PlatformServiceFactory> platformServiceFilter) throws Exception {
        srv = new EmbeddedService(BUNDLE, platformServiceFilter);
    }
    
    @Override
    public VpnManager<EmbeddedVpnConnection> manager() {
        return srv.getVpnManager();
    }

    @Override
    public IVpn<EmbeddedVpnConnection> vpn() {
        return srv.getVpnManager().getVpnOrFail();
    }

    public static void main(String[] args)  throws Exception {
        var embedder = new EmbedderApi((pf) -> true);
        Arrays.asList(embedder.vpn().getConnections()).forEach(vpn -> {
            System.out.println(vpn.getDisplayName());
        });
    }
}
