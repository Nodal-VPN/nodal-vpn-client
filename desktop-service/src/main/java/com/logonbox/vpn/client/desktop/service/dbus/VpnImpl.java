package com.logonbox.vpn.client.desktop.service.dbus;

import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.dbus.VPN;
import com.logonbox.vpn.client.common.dbus.VPNFrontEnd;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.desktop.service.DesktopServiceContext;
import com.logonbox.vpn.drivers.lib.DNSProvider;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.stream.Collectors;

@DBusInterfaceName("com.logonbox.vpn.VPN")
public class VpnImpl extends AbstractVPNComponent implements VPN {
	static Logger log = LoggerFactory.getLogger(VpnImpl.class);

	public VpnImpl(DesktopServiceContext ctx) {
		super(ctx);
	}

	@Override
	public String getObjectPath() {
		return OBJECT_PATH;
	}

	public boolean isRemote() {
		return true;
	}

    @Override
    public String[] getAvailableDNSMethods() {
        var l = new ArrayList<String>();
        for(var fact : ServiceLoader.load(DNSProvider.Factory.class))  {
            for(var clazz : fact.available()) {
                l.add(clazz.getName());
            }
        }
        return l.toArray(new String[0]);
    }

	@Override
	public String[] getKeys() {
		assertRegistered();
		return ctx.getClientService().getKeys();
	}


	@Override
	public String getUUID() {
		assertRegistered();
		UUID uuid = ctx.getClientService().getUUID(getCurrentUser());
		return uuid == null ? null : uuid.toString();
	}

	@Override
	public String getVersion() {
		assertRegistered();
        return ctx.getVersion();
	}

    @Override
	public void ping() {
		assertRegistered();
		ctx.getFrontEnd(getSourceOrDefault()).ping();
		ctx.getClientService().ping();
	}

	@Override
	public void deregister() {
		String src = getSourceOrDefault();
		log.info(String.format("De-register front-end %s", src));
		ctx.deregisterFrontEnd(src);
	}

	@Override
	public long importConfiguration(String configuration) {
		assertRegistered();
		return ctx.getClientService().importConfiguration(getCurrentUser(), configuration).getId();
	}

	@Override
	public void register(String username, boolean interactive, boolean supportsAuthorization) {
		try {
			VPNFrontEnd frontEnd = null;
			String source = getSourceOrDefault();
			log.info(String.format("Register client %s", source));
			if(ctx.hasFrontEnd(source)) {
				ctx.deregisterFrontEnd(source);
			}
			frontEnd = ctx.registerFrontEnd(source);
			frontEnd.setUsername(username);
			frontEnd.setInteractive(interactive);
			frontEnd.setSupportsAuthorization(supportsAuthorization);
	
			ctx.registered(frontEnd);
		}
		catch(Exception e) {
			log.error("Failed to register.", e);
		}
	}

	@Override
	public VpnConnection[] getConnections() {
		assertRegistered();
		try {
			return ctx.getClientService().getStatus(getCurrentUser()).stream()
					.map(c -> getConnection(c.getConnection().getId())).collect(Collectors.toList()).toArray(new VpnConnection[0]);
		} catch (Exception e) {	
			throw new IllegalStateException("Failed to get connections.", e);
		}
	}

    @Override
    public VpnConnection[] getAllConnections() {
        assertRegistered();
        try {
            return ctx.getClientService().getStatus("").stream()
                    .map(c -> getConnection(c.getConnection().getId())).collect(Collectors.toList()).toArray(new VpnConnection[0]);
        } catch (Exception e) { 
            throw new IllegalStateException("Failed to get connections.", e);
        }
    }

	@Override
	public String getDeviceName() {
		assertRegistered();
		return ctx.getClientService().getDeviceName();
	}

	@Override
	public long getConnectionIdForURI(String uri) {
		assertRegistered();
		try {
			return ctx.getClientService().getStatus(getCurrentUser(), uri).getConnection().getId();
		} catch (IllegalArgumentException iae) {
			return -1;
		}
	}

	@Override
	public long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode) {
		assertRegistered();
		try {
			ctx.getClientService().getStatus(getCurrentUser(), uri);
			throw new IllegalArgumentException(String.format("Connection with URI %s already exists.", uri));
		} catch (Exception e) {
			/* Doesn't exist */
			var fe = ctx.getFrontEnd(getSourceOrDefault());
			return ctx.getClientService().create(uri, fe == null ? System.getProperty("user.name") : fe.getUsername(), connectAtStartup, Mode.valueOf(mode), stayConnected).getId();
		}
	}

	@Override
	public long connect(String uri) {
		assertRegistered();
		return ctx.getClientService().connect(getCurrentUser(), uri).getId();
	}

	@Override
	public int getNumberOfConnections() {
		assertRegistered();
		return ctx.getClientService().getStatus(getCurrentUser()).size();
	}

	@Override
	public String getValue(String name) {
		assertRegistered();
		ConfigurationItem<?> item = ConfigurationItem.get(name);
		Object val = ctx.getClientService().getValue(getCurrentUser(), item); 
		return val.toString();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setValue(String name, String value) {
		assertRegistered();
		ctx.getClientService().setValue(getCurrentUser(), (ConfigurationItem<String>)ConfigurationItem.get(name), value);

	}

	@Override
	public int getIntValue(String key) {
		assertRegistered();
		return (Integer)ctx.getClientService().getValue(getCurrentUser(), ConfigurationItem.get(key));
	}

	@Override
	public long getLongValue(String key) {
		assertRegistered();
		return (Long)ctx.getClientService().getValue(getCurrentUser(), ConfigurationItem.get(key));
	}

	@Override
	public boolean getBooleanValue(String key) {
		assertRegistered();
		return (Boolean)ctx.getClientService().getValue(getCurrentUser(), ConfigurationItem.get(key));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setIntValue(String key, int value) {
		assertRegistered();
		ctx.getClientService().setValue(getCurrentUser(), (ConfigurationItem<Integer>)ConfigurationItem.get(key), value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setLongValue(String key, long value) {
		assertRegistered();
		ctx.getClientService().setValue(getCurrentUser(), (ConfigurationItem<Long>)ConfigurationItem.get(key), value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setBooleanValue(String key, boolean value) {
		assertRegistered();
		ctx.getClientService().setValue(getCurrentUser(), (ConfigurationItem<Boolean>)ConfigurationItem.get(key), value);		
	}

	@Override
	public void disconnectAll() {
		assertRegistered();
		for (ConnectionStatus s : ctx.getClientService().getStatus(getCurrentUser())) {
			if (s.getStatus() != Type.DISCONNECTED && s.getStatus() != Type.DISCONNECTING) {
				try {
					ctx.getClientService().disconnect(s.getConnection(), null);
				} catch (Exception re) {
					log.error("Failed to disconnect " + s.getConnection().getId(), re);
				}
			}
		}
	}

	@Override
	public int getActiveButNonPersistentConnections() {
		assertRegistered();
		int active = 0;
		for (ConnectionStatus s : ctx.getClientService().getStatus(getCurrentUser())) {
			if (s.getStatus() == Type.CONNECTED && !s.getConnection().isStayConnected()) {
				active++;
			}
		}
		return active;
	}

	@Override
	public long getMaxMemory() {
		return Runtime.getRuntime().maxMemory();
	}

	@Override
	public long getFreeMemory() {
		return Runtime.getRuntime().freeMemory();
	}

	@Override
	public void shutdown(boolean restart) {
		ctx.shutdown(restart);
		
	}

	@Override
	public boolean isCertAccepted(String encodedKey) {
		return ctx.getCertManager().isAccepted(encodedKey);
	}

	@Override
	public void acceptCert(String encodedKey) {
		ctx.getCertManager().accept(encodedKey);
	}

	@Override
	public void saveCert(String encodedKey) {
		ctx.getCertManager().save(encodedKey);
	}

	@Override
	public void rejectCert(String encodedKey) {
		ctx.getCertManager().reject(encodedKey);
	}

	@Override
	public boolean isMatchesAnyServerURI(String uri) {
		assertRegistered();
		return ctx.getClientService().isMatchesAnyServerURI(getCurrentUser(), uri);
	}

    @Override
    public VpnConnection getConnection(long id) {
        assertRegistered();
        try {
            return ctx.getConnection().getExportedObject(getDeviceName(), "/com/logonbox/vpn/" + id, VpnConnection.class);
        } catch (DBusException e) {
            throw new IllegalStateException("Failed to get connection.", e);
        }
    }
}
