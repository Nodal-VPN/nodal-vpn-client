package com.logonbox.vpn.client.service;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.api.IVpnConnection;

public interface ClientContext<CONX extends IVpnConnection> {

	LocalContext<CONX> getLocalContext();
}
