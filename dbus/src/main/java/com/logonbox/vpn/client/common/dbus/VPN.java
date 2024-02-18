package com.logonbox.vpn.client.common.dbus;

import com.logonbox.vpn.client.common.api.IVpn;

import org.freedesktop.dbus.annotations.DBusBoundProperty;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

import java.util.List;

import uk.co.bithatch.nativeimage.annotations.Proxy;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Proxy
@Reflectable
@TypeReflect(methods = true, classes = true)
@DBusInterfaceName("com.logonbox.vpn.VPN")
public interface VPN extends IVpn<VpnConnection>, DBusInterface {
    
    final static String OBJECT_PATH = "/com/logonbox/vpn";

    @DBusMemberName("Ping")
    void ping();

    @DBusMemberName("Deregister")
    void deregister();

    @DBusMemberName("Register")
    void register(String username, boolean interactive, boolean supportsAuthorization);
    
    @Override
    @DBusBoundProperty
    String[] getAvailableDNSMethods();
    
    @Override
    @DBusBoundProperty
    long getMaxMemory();

    @Override
    @DBusBoundProperty
    long getFreeMemory();

    @Override
    @DBusBoundProperty
    String getUUID();

    @Override
    @DBusBoundProperty
    String getVersion();

    @Override
    @DBusBoundProperty
    String getDeviceName();

    @Override
    @DBusMemberName("Shutdown")
    void shutdown(boolean restart);

    @Override
    @DBusMemberName("ImportConfiguration")
    long importConfiguration(String configuration);

    @Override
    @DBusMemberName("GetConnectionIdForURI")
    long getConnectionIdForURI(String uri);

    @Override
    @DBusMemberName("CreateConnection")
    long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode);

    @Override
    @DBusBoundProperty
    int getNumberOfConnections();

    @Override
    @DBusMemberName("Connect")
    long connect(String uri);

    @Override
    @DBusMemberName("GetValue")
    String getValue(String key);

    @Override
    @DBusMemberName("GetIntValue")
    int getIntValue(String key);

    @Override
    @DBusMemberName("GetLongValue")
    long getLongValue(String key);

    @Override
    @DBusMemberName("GetBooleanValue")
    boolean getBooleanValue(String key);

    @Override
    @DBusMemberName("SetValue")
    void setValue(String key, String value); 

    @Override
    @DBusMemberName("SetIntValue")
    void setIntValue(String key, int value); 

    @Override
    @DBusMemberName("SetLongValue")
    void setLongValue(String key, long value); 

    @Override
    @DBusMemberName("SetBooleanValue")
    void setBooleanValue(String key, boolean value);

    @Override
    @DBusMemberName("DisconnectAll")
    void disconnectAll();

    @Override
    @DBusBoundProperty
    int getActiveButNonPersistentConnections();

    @Override
    @DBusMemberName("IsCertAccepted")
    boolean isCertAccepted(String encodedKey);

    @Override
    @DBusMemberName("AcceptCert")
    void acceptCert(String encodedKey);

    @Override
    @DBusMemberName("RejectCert")
    void rejectCert(String encodedKey);

    @Override
    @DBusMemberName("SaveCert")
    void saveCert(String encodedKey);

    @Override
    @DBusMemberName("GetKeys")
    String[] getKeys();

    @Override
    @DBusMemberName("IsMatchesAnyServerURI")
    boolean isMatchesAnyServerURI(String uri);
    
    @Override
    @DBusMemberName("GetConnections")
    List<VpnConnection> getConnections();
    
    @Override
    @DBusMemberName("GetConnection")
    VpnConnection getConnection(long id);

//
    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class CertificatePrompt extends DBusSignal {

		private final String alertType;
		private final String title;
		private final String content;
		private final String key;
		private final String hostname;
		private final String message;

		public CertificatePrompt(String path, String alertType, String title, String content, String key,
				String hostname, String message) throws DBusException {
			super(path, alertType, title, content, key,
					hostname, message);
			this.alertType = alertType;
			this.title = title;
			this.content = content;
			this.hostname = hostname;
			this.message = message;
			this.key = key;
		}

		public String getAlertType() {
			return alertType;
		}

		public String getTitle() {
			return title;
		}

		public String getContent() {
			return content;
		}

		public String getKey() {
			return key;
		}

		public String getHostname() {
			return hostname;
		}

		public String getMessage() {
			return message;
		}
	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class ConnectionAdded extends DBusSignal {

		private final Long id;

		public ConnectionAdded(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class ConnectionAdding extends DBusSignal {

		public ConnectionAdding(String path) throws DBusException {
			super(path);
		}
	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class ConnectionRemoved extends DBusSignal {

		private final Long id;

		public ConnectionRemoved(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class ConnectionRemoving extends DBusSignal {

		private final Long id;

		public ConnectionRemoving(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class ConnectionUpdated extends DBusSignal {

		private final Long id;

		public ConnectionUpdated(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class ConnectionUpdating extends DBusSignal {

		private final Long id;

		public ConnectionUpdating(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public class GlobalConfigChange extends DBusSignal {

		private final String name;
		private final String value;

		public GlobalConfigChange(String path, String name, String value) throws DBusException {
			super(path, name, value);
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

	}

    @Reflectable
    @TypeReflect(methods = true, constructors = true)
	public static  class Exit extends DBusSignal {
		public Exit(String path) throws DBusException {
			super(path);
		}
	}

}
