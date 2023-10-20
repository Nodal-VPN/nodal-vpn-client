package com.logonbox.vpn.client.desktop.service.dbus;

import com.logonbox.vpn.client.AbstractService;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.HypersocketVersion;
import com.logonbox.vpn.client.common.api.IVPNConnection;
import com.logonbox.vpn.client.common.dbus.VPN;
import com.logonbox.vpn.client.common.dbus.VPNFrontEnd;
import com.logonbox.vpn.client.desktop.service.DesktopServiceContext;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@DBusInterfaceName("com.logonbox.vpn.VPN")
public class VPNImpl extends AbstractVPNComponent implements VPN {
	static Logger log = LoggerFactory.getLogger(VPNImpl.class);

	private DesktopServiceContext ctx;

	public VPNImpl(DesktopServiceContext ctx) {
		super(ctx);
		this.ctx = ctx;
	}

	@Override
	public String getObjectPath() {
		return "/com/logonbox/vpn";
	}

	public boolean isRemote() {
		return true;
	}

	@Override
	public String[] getKeys() {
		assertRegistered();
		return ctx.getClientService().getKeys();
	}


	@Override
	public String getUUID() {
		assertRegistered();
		UUID uuid = ctx.getClientService().getUUID(getOwner());
		return uuid == null ? null : uuid.toString();
	}

	@Override
	public String getVersion() {
		assertRegistered();
		return HypersocketVersion.getVersion(AbstractService.ARTIFACT_COORDS);
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
		return ctx.getClientService().importConfiguration(getOwner(), configuration).getId();
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
	public List<IVPNConnection> getConnections() {
		assertRegistered();
		try {
			return ctx.getClientService().getStatus(getOwner()).stream()
					.map(c -> new VPNConnectionImpl(ctx, c.getConnection())).collect(Collectors.toList());
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
			return ctx.getClientService().getStatus(getOwner(), uri).getConnection().getId();
		} catch (IllegalArgumentException iae) {
			return -1;
		}
	}

	@Override
	public long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode) {
		assertRegistered();
		try {
			ctx.getClientService().getStatus(getOwner(), uri);
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
		return ctx.getClientService().connect(getOwner(), uri).getId();
	}

	@Override
	public int getNumberOfConnections() {
		assertRegistered();
		return ctx.getClientService().getStatus(getOwner()).size();
	}

	@Override
	public String getValue(String name) {
		assertRegistered();
		ConfigurationItem<?> item = ConfigurationItem.get(name);
		Object val = ctx.getClientService().getValue(getOwner(), item); 
		return val.toString();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setValue(String name, String value) {
		assertRegistered();
		ctx.getClientService().setValue(getOwner(), (ConfigurationItem<String>)ConfigurationItem.get(name), value);

	}

	@Override
	public int getIntValue(String key) {
		assertRegistered();
		return (Integer)ctx.getClientService().getValue(getOwner(), ConfigurationItem.get(key));
	}

	@Override
	public long getLongValue(String key) {
		assertRegistered();
		return (Long)ctx.getClientService().getValue(getOwner(), ConfigurationItem.get(key));
	}

	@Override
	public boolean getBooleanValue(String key) {
		assertRegistered();
		return (Boolean)ctx.getClientService().getValue(getOwner(), ConfigurationItem.get(key));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setIntValue(String key, int value) {
		assertRegistered();
		ctx.getClientService().setValue(getOwner(), (ConfigurationItem<Integer>)ConfigurationItem.get(key), value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setLongValue(String key, long value) {
		assertRegistered();
		ctx.getClientService().setValue(getOwner(), (ConfigurationItem<Long>)ConfigurationItem.get(key), value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setBooleanValue(String key, boolean value) {
		assertRegistered();
		ctx.getClientService().setValue(getOwner(), (ConfigurationItem<Boolean>)ConfigurationItem.get(key), value);		
	}

	@Override
	public void disconnectAll() {
		assertRegistered();
		for (ConnectionStatus s : ctx.getClientService().getStatus(getOwner())) {
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
		for (ConnectionStatus s : ctx.getClientService().getStatus(getOwner())) {
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
		return ctx.getClientService().isMatchesAnyServerURI(getOwner(), uri);
	}

    @Override
    public IVPNConnection getConnection(long id) {
        assertRegistered();
        return new VPNConnectionImpl(ctx, ctx.getClientService().getStatus(id).getConnection());
    }
}
