package com.logonbox.vpn.common.client;

import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;

public interface Connection {

	@Deprecated
	public enum Mode {
		CLIENT, SERVICE, NODE, PROVIDER, PEER, MODERN
	}
	
	Mode getMode();
	
	void setMode(Mode mode);
	
	AuthMethod[] getAuthMethods();
	
	void setAuthMethods(AuthMethod[] methods);
	
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
		}
		else
			return dn;
	}

	default String getDefaultDisplayName() {
		String uri = getUsernameHint();
		if(uri == null)
			uri = "";
		else if(!uri.equals(""))
			uri += "@";
		if(getHostname() == null || getHostname().length() == 0) {
			uri += getEndpointAddress();
			uri += ":" + getEndpointPort();
		}
		else {
			uri += getHostname();
			if (getPort() != 443) {
				uri += ":" + getPort();
			}
		}
		return uri;
	}

	void setName(String name);

	default String getApiUri() {
		if(getHostname() == null) {
			throw new IllegalStateException("No API url for this type.");
		}
		else {
			if(getMode().equals(Mode.MODERN)) {
				String uri = "https://";
				uri += getHostname();
				if (getPort() != 443) {
					uri += ":" + getPort();
				}
				return uri;	
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
		uri += lastKnownServerIpAddress == null || lastKnownServerIpAddress.equals("") ? getHostname() : lastKnownServerIpAddress;
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

	String getUserPrivateKey();

	void setUserPrivateKey(String privateKey);

	String getUserPublicKey();

	void setUserPublicKey(String publicKey);

	String getPublicKey();

	void setPublicKey(String Key);

	void setEndpointAddress(String endpointAddress);

	void setEndpointPort(int endpoingPort);

	String getEndpointAddress();

	int getEndpointPort();

	int getMtu();

	void setMtu(int mtu);

	String getAddress();

	void setAddress(String address);

	List<String> getDns();

	void setDns(List<String> dns);

	int getPersistentKeepalive();

	void setPeristentKeepalive(int peristentKeepalive);

	List<String> getAllowedIps();

	void setAllowedIps(List<String> allowedIps);

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
	
	String getPreUp();
	
	String getPostUp();
	
	String getPreDown();
	
	String getPostDown();
	
	boolean isRouteAll();

	default void updateFromUri(String uri) {
		try {
			URI uriObj = Util.getUri(uri);
			setHostname(uriObj.getHost());
			Util.setLastKnownServerIpAddress(this);
			setPort(uriObj.getPort() >= 0 ? uriObj.getPort() : 443);
			setPath(uriObj.getPath());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid URI.", e);
		}
	}

	void setRouteAll(boolean routeAll);

	void setPreUp(String preUp);

	void setPostUp(String postUp);

	void setPreDown(String preDown);

	void setPostDown(String postDown);

	void setError(String error);
	
	String getError();

    default boolean isLogonBoxVPN() {
        return StringUtils.isNotBlank(getHostname());
    }

}
