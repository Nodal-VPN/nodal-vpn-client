package com.logonbox.vpn.client.common;

import com.logonbox.vpn.drivers.lib.VpnConfiguration;

import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;

public interface Connection extends VpnConfiguration {

    public enum Mode {
        CLIENT, SERVICE, NODE, PROVIDER, PEER
    }

    Mode getMode();

    void setMode(Mode mode);

    void setLastKnownServerIpAddress(String lastKnownServerIpAddress);

    String getLastKnownServerIpAddress();

    default boolean isTransient() {
        return getId() == null || getId() < 0;
    }

    void setPort(Integer port);

    void setConnectAtStartup(boolean connectAtStartup);

    boolean isConnectAtStartup();

    void setStayConnected(boolean stayConnected);

    boolean isStayConnected();

    void setPath(String path);

    String getPath();

    int getPort();

    void setHostname(String hostname);

    String getHostname();

    String getName();

    default String getDisplayName() {
        String dn = getName();
        if (dn == null || dn.length() == 0) {
            return getDefaultDisplayName();
        } else
            return dn;
    }

    default String getDefaultDisplayName() {
        String uri = getUsernameHint();
        if (uri == null)
            uri = "";

        if (getHostname() == null || getHostname().length() == 0) {
            if (!peers().isEmpty()) {
                var peer = peers().get(0);
                if (peer.endpointAddress().isPresent()) {
                    if (!uri.equals(""))
                        uri += "@";
                    uri += peer.endpointAddress().get();
                    if (peer.endpointPort().isPresent())
                        uri += ":" + peer.endpointPort().get();
                }
            }
        } else {
            if (!uri.equals(""))
                uri += "@";
            uri += getHostname();
            if (getPort() != 443) {
                uri += ":" + getPort();
            }
        }
        if(uri.equals("")) {
            uri = "Unnamed";
        }
        return uri;
    }

    void setName(String name);

    default String getUri(boolean withUsername) {
        if (getHostname() == null) {
            try {
                if (withUsername)
                    return "wg://" + URLEncoder.encode(publicKey(), "UTF-8") + "@" + getEndpointAddress() + ":"
                            + getEndpointPort();
            } catch (UnsupportedEncodingException e) {
            }
            return "wg://" + getEndpointAddress() + ":" + getEndpointPort();
        } else {
            String uri = "https://";
            uri += getHostname();
            if (getPort() != 443) {
                uri += ":" + getPort();
            }
            uri += getPath();
            return uri;
        }
    }

    default String getConnectionTestUri(boolean withUsername) {
        String uri = "https://";
        var lastKnownServerIpAddress = getLastKnownServerIpAddress();
        uri += lastKnownServerIpAddress == null || lastKnownServerIpAddress.equals("") ? getHostname()
                : lastKnownServerIpAddress;
        if (getPort() != 443) {
            uri += ":" + getPort();
        }
        uri += getPath();
        return uri;
    }

    String getUsernameHint();

    void setUsernameHint(String usernameHint);

    void setId(Long id);

    Long getId();

    boolean isAuthorized();

    void deauthorize();

    boolean isShared();

    void setShared(boolean shared);

    String getOwner();

    default String getOwnerOrCurrent() {
        var owner = getOwner();
        return owner == null || owner.equals("") ? System.getProperty("user.name") : owner;
    }

    void setOwner(String owner);

    default void updateFromUri(String uri) {
        try {
            URI uriObj = ConnectionUtil.getUri(uri);
            setHostname(uriObj.getHost());
            ConnectionUtil.setLastKnownServerIpAddress(this);
            setPort(uriObj.getPort() >= 0 ? uriObj.getPort() : 443);
            setPath(uriObj.getPath());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI.", e);
        }
    }

    void setRouteAll(boolean routeAll);

    default boolean isLogonBoxVPN() {
        return StringUtils.isNotBlank(getHostname());
    }

    void setUserPrivateKey(String privateKey);

    void setUserPublicKey(String publicKey);

    void setMtu(int mtu);

    void setAddress(String address);

    void setError(String error);

    void setPreUp(String preUp);

    void setPostUp(String postUp);

    void setPreDown(String preDown);

    void setPostDown(String postDown);

    void setDns(List<String> dns);

    void setEndpointAddress(String endpointAddress);

    void setEndpointPort(int endpoingPort);

    void setPublicKey(String Key);

    void setPeristentKeepalive(int peristentKeepalive);

    void setAllowedIps(List<String> allowedIps);

    boolean isRouteAll();

    String getPostUp();

    String getPreDown();

    String getPostDown();

    String getPreUp();

    String getAddress();

    List<String> getAllowedIps();

    List<String> getDns();

    String getEndpointAddress();

    int getEndpointPort();

    int getMtu();

    int getPersistentKeepalive();

    String getPublicKey();

    String getUserPublicKey();

    String getError();

    String getUserPrivateKey();

    String getPresharedKey();

    void setPresharedKey(String presharedKey);

    int getFwMark();

    void setFwMark(int fwMark);

    String getTable();

    void setTable(String table);

    void setSaveConfig(boolean saveConfig);
    
    boolean isSaveConfig();

}
