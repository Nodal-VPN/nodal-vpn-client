package com.logonbox.vpn.client.service;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.api.IVPNConnection;

public interface ClientContext<CONX extends IVPNConnection> {

	LocalContext<CONX> getLocalContext();
}
