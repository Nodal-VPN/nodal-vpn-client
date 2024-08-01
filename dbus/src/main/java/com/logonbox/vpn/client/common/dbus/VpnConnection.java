package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.api.IVpnConnection;

import org.freedesktop.dbus.annotations.DBusBoundProperty;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

import uk.co.bithatch.nativeimage.annotations.Proxy;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Proxy
@Reflectable
@TypeReflect(methods = true, classes = true)
@DBusInterfaceName("com.logonbox.vpn.Connection")
public interface VpnConnection extends DBusInterface, IVpnConnection {
    
    @Override
    @DBusMemberName("Update")
    void update(String name, String uri, boolean connectAtStartup, boolean stayConnected);

    @Override
    @DBusMemberName("Save")
    long save();

    @Override
    @DBusBoundProperty
    String getHostname();

    @Override
    @DBusBoundProperty
    String getInstance();

    @Override
    @DBusBoundProperty
    String getSerial();

    @Override
    @DBusBoundProperty
    String getMode();
    
    @Override
    @DBusBoundProperty
    String[] getAuthMethods();

    @Override
    @DBusBoundProperty
    int getPort();

    @Override
    @DBusMemberName("GetUri")
    String getUri(boolean withUsername);

    @Override
    @DBusMemberName("GetConnectionTestUri")
    String getConnectionTestUri(boolean withUsername);

    @Override
    @DBusBoundProperty
    boolean isTransient();

    @Override
    @DBusBoundProperty
    long getId();

    @Override
    @DBusBoundProperty
    String getName();

    @Override
    @DBusBoundProperty
    String getInterfaceName();

    @Override
    @DBusBoundProperty
    String getDisplayName();

    @Override
    @DBusBoundProperty
    String getDefaultDisplayName();

    @Override
    @DBusBoundProperty
    boolean isConnectAtStartup();

    @Override
    @DBusBoundProperty
    boolean isTemporarilyOffline();

    @Override
    @DBusBoundProperty
    boolean isBlocked();

    @Override
    @DBusBoundProperty
    boolean isStayConnected();

    @Override
    @DBusBoundProperty
    void setStayConnected(boolean stayConnected);

    @Override
    @DBusMemberName("Delete")
    void delete();

    @Override
    @DBusMemberName("Disconnect")
    void disconnect(String reason);

    @Override
    @DBusMemberName("Connect")
    void connect();

    @Override
    @DBusBoundProperty
    String getStatus();

    @Override
    @DBusMemberName("Authorize")
    void authorize();

    @Override
    @DBusMemberName("Authorized")
    void authorized();

    @Override
    @DBusMemberName("Deauthorized")
    void deauthorize();

    @Override
    @DBusBoundProperty
    String getUsernameHint();

    @Override
    @DBusBoundProperty
    String getUserPublicKey();

    @Override
    @DBusBoundProperty
    boolean hasPrivateKey();

    @Override
    @DBusBoundProperty
    String getPublicKey();

    @Override
    @DBusBoundProperty
    String getEndpointAddress();

    @Override
    @DBusBoundProperty
    int getEndpointPort();

    @Override
    @DBusBoundProperty
    int getMtu();

    @Override
    @DBusBoundProperty
    String getAddress();

    @Override
    @DBusBoundProperty
    String[] getDns();

    @Override
    @DBusBoundProperty
    int getPersistentKeepalive();

    @Override
    @DBusBoundProperty
    String[] getAllowedIps();

    @Override
    @DBusBoundProperty
    boolean isAuthorized();

    @Override
    @DBusBoundProperty
    boolean isShared();

    @Override
    @DBusBoundProperty
    boolean isFavourite();

    @Override
    @DBusMemberName("SetAsFavourite")
    void setAsFavourite();

    @Override
    @DBusBoundProperty
    String getOwner();

    @Override
    @DBusBoundProperty
    String getClient();

    @Override
    @DBusBoundProperty
    long getLastHandshake();

    @Override
    @DBusBoundProperty
    long getRx();

    @Override
    @DBusBoundProperty
    long getTx();

    @Override
    @DBusBoundProperty
    void setConnectAtStartup(boolean connectAtStartup);

    @Override
    @DBusBoundProperty
    void setName(String name);

    @Override
    @DBusBoundProperty
    void setSerial(String serial);

    @Override
    @DBusBoundProperty
    void setHostname(String host);

    @Override
    @DBusBoundProperty
    void setPort(int port);

    @Override
    @DBusBoundProperty
    void setUsernameHint(String usernameHint);

    @Override
    @DBusBoundProperty
    void setUserPrivateKey(String base64PrivateKey);

    @Override
    @DBusBoundProperty
    void setUserPublicKey(String base64PublicKey);

    @Override
    @DBusBoundProperty
    void setEndpointAddress(String endpointAddress);

    @Override
    @DBusBoundProperty
    void setEndpointPort(int endpointPort);

    @Override
    @DBusBoundProperty
    void setPersistentKeepalive(int peristentKeepalive);

    @Override
    @DBusBoundProperty
    void setPublicKey(String publicKey);

    @Override
    @DBusBoundProperty
    void setAllowedIps(String[] allowedIps);

    @Override
    @DBusBoundProperty
    void setAddress(String address);

    @Override
    @DBusBoundProperty
    void setDns(String[] dns);

    @Override
    @DBusBoundProperty
    void setPath(String path);

    @Override
    @DBusBoundProperty
    void setOwner(String owner);

    @Override
    @DBusBoundProperty
    void setShared(boolean shared);

    @Override
    @DBusBoundProperty
    void setPreUp(String preUp);

    @Override
    @DBusBoundProperty
    void setPostUp(String preUp);

    @Override
    @DBusBoundProperty
    void setPreDown(String preDown);

    @Override
    @DBusBoundProperty
    void setPostDown(String preDown);

    @Override
    @DBusBoundProperty
    void setRouteAll(boolean routeAll);

    @Override
    @DBusBoundProperty
    boolean isRouteAll();

    @Override
    @DBusBoundProperty
    String getPreUp();

    @Override
    @DBusBoundProperty
    String getPostUp();

    @Override
    @DBusBoundProperty
    String getPreDown();

    @Override
    @DBusBoundProperty
    String getPostDown();

    @Override
    @DBusBoundProperty
    String getPath();

    @Override
    @DBusMemberName("Parse")
    String parse(String configIniFile);

    @Override
    @DBusBoundProperty
    String getLastError();

    @Override
    @DBusBoundProperty
    String getAuthorizeUri();

    @Override
    @DBusBoundProperty
    String getBaseUri();

    @Override
    @DBusBoundProperty
    String getApiUri();


    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public static class Connected extends DBusSignal {
		public Connected(String path) throws DBusException {
			super(path);
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}


    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public static class Connecting extends DBusSignal {
		public Connecting(String path) throws DBusException {
			super(path);
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}


    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class Failed extends DBusSignal {

		private final String reason;
		private final String cause;
		private final String trace;

		public Failed(String path, String reason, String cause, String trace) throws DBusException {
			super(path, reason, cause, trace);
			this.cause = cause;
			this.trace = trace;
			this.reason = reason;
		}

		public String getCause() {
			return cause;
		}

		public String getTrace() {
			return trace;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}


    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public static class TemporarilyOffline extends DBusSignal {

		private final String reason;

		public TemporarilyOffline(String path, String reason) throws DBusException {
			super(path, reason);
			this.reason = reason;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}


    @Reflectable
    @TypeReflect(methods = true, constructors = true)
    public static class Blocked extends DBusSignal {

        public Blocked(String path) throws DBusException {
            super(path);
        }
        
        public long getId() {
            return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
        }
    }


    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public static class Disconnected extends DBusSignal {

		private final String reason;
		private final String displayName;
		private final String hostname;

		public Disconnected(String path, String reason, String displayName, String hostname) throws DBusException {
			super(path, reason, displayName, hostname);
			this.reason = reason;
			this.displayName = displayName;
			this.hostname = hostname;
		}

		public String getDisplayName() {
			return displayName;
		}

		public String getHostname() {
			return hostname;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}


    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public static class Disconnecting extends DBusSignal {

		private final String reason;

		public Disconnecting(String path, String reason) throws DBusException {
			super(path, reason);
			this.reason = reason;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public static class Authorize extends DBusSignal {

		private final String uri;
		private final String mode;
        private final boolean legacy;
		
		public Authorize(String path, String uri, String mode, boolean legacy) throws DBusException {
			super(path, uri, mode, legacy);
			this.uri = uri;
			this.mode = mode;
            this.legacy = legacy;
		}

        public boolean isLegacy() {
            return legacy;
        }

		public String getMode() {
			return mode;
		}

		public String getUri() {
			return uri;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}



}
