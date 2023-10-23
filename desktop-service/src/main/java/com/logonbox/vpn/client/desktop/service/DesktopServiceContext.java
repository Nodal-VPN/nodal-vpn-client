package com.logonbox.vpn.client.desktop.service;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.dbus.VPNConnection;
import com.logonbox.vpn.client.common.dbus.VPNFrontEnd;

import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.messages.Message;

import java.util.Collection;

public interface DesktopServiceContext extends LocalContext<VPNConnection> {

    void deregisterFrontEnd(String source);

    boolean hasFrontEnd(String source);

    VPNFrontEnd registerFrontEnd(String source);

    void registered(VPNFrontEnd frontEnd);

    VPNFrontEnd getFrontEnd(String source);

    boolean isRegistrationRequired();

    Collection<VPNFrontEnd> getFrontEnds();

    void sendMessage(Message message);

    AbstractConnection getConnection();

}
