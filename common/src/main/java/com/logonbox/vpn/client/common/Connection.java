package com.logonbox.vpn.client.common;

import com.jadaptive.nodal.core.lib.VpnConfiguration;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;

public interface Connection extends VpnConfiguration {

    public enum Mode {
        CLIENT, SERVICE, NODE, PROVIDER, PEER, MODERN
    }
    
    AuthMethod[] getAuthMethods();
    
    void setAuthMethods(AuthMethod[] methods);

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


    default String getClient() {
        var path = getPath();
        while(path.startsWith("/"))
            path = path.substring(1);
        if(path.startsWith("vpn/")) {
            return path.substring(4);
        }
        return "";
    }

    default String getBaseUri() {
        if(getHostname() == null) {
            throw new IllegalStateException("No API url for this type.");
        }
        else {
            String uri = "https://";
            uri += getHostname();
            if (getPort() != 443) {
                uri += ":" + getPort();
            }
            return uri; 
        }
    }

    default String getBaseUri(boolean withUsername) {
        if(getHostname() == null) {
            throw new IllegalStateException("No API url for this type.");
        }
        else {
            String uri = "https://";
            if(withUsername && Utils.isNotBlank(getUsernameHint())) {
                try {
                    uri += URLEncoder.encode(getUsernameHint(), "UTF-8") + "@";
                } catch (UnsupportedEncodingException e) {
                }
            }
            uri += getHostname();
            if (getPort() != 443) {
                uri += ":" + getPort();
            }
            return uri; 
        }
    }

    default String getApiUri() {
        if(getHostname() == null) {
            throw new IllegalStateException("No API url for this type.");
        }
        else {
            if(getMode().equals(Mode.MODERN)) {
                return getBaseUri();
            }
            else {
                return getUri(false);
            }
        }
    }

    default String getUri(boolean withUsername) {
        if(getHostname() == null) {
            try {
                if(withUsername)
                    return "wg://" + URLEncoder.encode(getUserPublicKey(), "UTF-8") + "@" + getEndpointAddress() + ":" + getEndpointPort();
            } catch (UnsupportedEncodingException e) {
            }
            return "wg://" + getEndpointAddress() + ":" + getEndpointPort();
        }
        else {
            String uri = getBaseUri(withUsername);
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
        if(getMode().equals(Mode.MODERN))
            uri += getPath();
        else
            uri += "/app";
        return uri;
    }

    String getUsernameHint();

    String getSerial();
    
    void setUsernameHint(String usernameHint);

    void setId(Long id);

    Long getId();

    boolean isAuthorized();

    void deauthorize();

    boolean isShared();

    void setShared(boolean shared);

    String getOwner();
    
    String getInstance();

    void setInstance(String instance);

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
        return Utils.isNotBlank(getHostname());
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

    void setPersistentKeepalive(int peristentKeepalive);

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

    void setSerial(String serial);

}
