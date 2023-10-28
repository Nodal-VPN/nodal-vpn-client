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
