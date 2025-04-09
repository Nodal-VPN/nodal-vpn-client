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
package com.logonbox.vpn.client.common.api;

public interface IVpnConnection {
    static final String PRIVATE_KEY_NOT_AVAILABLE = "PRIVATE_KEY_NOT_AVAILABLE";

    void update(String name, String uri, boolean connectAtStartup, boolean stayConnected);

    long save();

    String getHostname();

    String getMode();

    int getPort();

    String getBaseUri();

    String getApiUri();

    String getUri(boolean withUsername);

    String getConnectionTestUri(boolean withUsername);

    boolean isTransient();

    long getId();

    String getName();

    String getInterfaceName();

    String getDisplayName();

    String getDefaultDisplayName();

    boolean isConnectAtStartup();

    boolean isTemporarilyOffline();

    boolean isBlocked();

    boolean isStayConnected();

    void setStayConnected(boolean stayConnected);

    void delete();

    void disconnect(String reason);

    void connect();
    
    String getSerial();

    String getStatus();

    void authorize();

    void authorized();

    void deauthorize();

    String getUsernameHint();

    String getUserPublicKey();

    String getInstance();

    boolean hasPrivateKey();

    String getPublicKey();

    String getEndpointAddress();

    int getEndpointPort();

    int getMtu();

    String getAddress();

    String[] getDns();

    int getPersistentKeepalive();

    String[] getAllowedIps();

    boolean isAuthorized();

    boolean isShared();

    boolean isFavourite();

    void setAsFavourite();

    String getOwner();

    long getLastHandshake();

    long getRx();

    long getTx();

    void setConnectAtStartup(boolean connectAtStartup);

    void setName(String name);

    void setSerial(String serial);

    void setHostname(String host);

    void setPort(int port);

    void setUsernameHint(String usernameHint);

    void setUserPrivateKey(String base64PrivateKey);

    void setUserPublicKey(String base64PublicKey);

    void setEndpointAddress(String endpointAddress);

    void setEndpointPort(int endpointPort);

    void setPersistentKeepalive(int peristentKeepalive);

    void setPublicKey(String publicKey);

    void setAllowedIps(String[] allowedIps);

    void setAddress(String address);

    void setDns(String[] dns);

    void setPath(String path);

    void setOwner(String owner);

    void setShared(boolean shared);

    void setPreUp(String preUp);

    void setPostUp(String preUp);

    void setPreDown(String preDown);

    void setPostDown(String preDown);

    void setRouteAll(boolean routeAll);

    boolean isRouteAll();

    String getPreUp();

    String getPostUp();

    String getPreDown();

    String getPostDown();

    String getPath();

    String parse(String configIniFile);

    String getLastError();

    String getAuthorizeUri();

    String[] getAuthMethods();

    String getClient();

}
