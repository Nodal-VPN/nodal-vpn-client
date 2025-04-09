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

import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.util.ServiceLoader;

public interface Api<CONX extends IVpnConnection> {

    final static class DefaultApi {
        private final static Api<? extends IVpnConnection> INSTANCE = get();

        @SuppressWarnings("unchecked")
        private static Api<? extends IVpnConnection> get() {
            return ServiceLoader.load(Api.class).findFirst().orElseThrow(() -> new IllegalStateException(
                    "There are no implementations of the service ''{0}''. Check your classpath or modulepath contains an implementation."));
        }
    }

    @SuppressWarnings("unchecked")
    public static <CONX extends IVpnConnection> Api<CONX> get() {
        return (Api<CONX>) DefaultApi.INSTANCE;
    }

    IVpn<CONX> vpn();

    VpnManager<CONX> manager();
}
