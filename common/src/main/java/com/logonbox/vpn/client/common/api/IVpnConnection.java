package com.logonbox.vpn.client.common.api;

public interface IVpnConnection {
    static final String PRIVATE_KEY_NOT_AVAILABLE = "PRIVATE_KEY_NOT_AVAILABLE";

    void update(String name, String uri, boolean connectAtStartup, boolean stayConnected);

    long save();

    String getHostname();

    String getMode();

    int getPort();

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

    boolean isStayConnected();

    void setStayConnected(boolean stayConnected);

    void delete();

    void disconnect(String reason);

    void connect();

    String getStatus();

    void authorize();

    void authorized();

    void deauthorize();

    String getUsernameHint();

    String getUserPublicKey();

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

}
