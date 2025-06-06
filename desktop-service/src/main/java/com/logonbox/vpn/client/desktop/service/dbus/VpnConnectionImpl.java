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
package com.logonbox.vpn.client.desktop.service.dbus;

import com.jadaptive.nodal.core.lib.util.Keys;
import com.logonbox.vpn.client.common.AuthMethod;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionUtil;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.dbus.VpnConnection;
import com.logonbox.vpn.client.desktop.service.DesktopServiceContext;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.MultiValueMode;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;

@DBusInterfaceName("com.logonbox.vpn.Connection")
public class VpnConnectionImpl extends AbstractVPNComponent implements VpnConnection {
    static Logger log = LoggerFactory.getLogger(VpnConnectionImpl.class);

	private Connection connection;
	private DesktopServiceContext ctx;

	public VpnConnectionImpl(DesktopServiceContext ctx, Connection connection) {
		super(ctx);
		this.connection = connection;
		this.ctx = ctx;
	}

	@Override
	public String parse(String configIniFile) {
		try {
		    var rdr = new INIReader.Builder().
                    withMultiValueMode(MultiValueMode.SEPARATED).
		            build();
		    
		    var ini = rdr.read(configIniFile);
	
			/* Interface (us) */
			var interfaceSection = ini.section("Interface");
			setAddress(interfaceSection.get("Address"));
			setDns(interfaceSection.getAllElse("DNS", new String[0]));
	
			var privateKey = interfaceSection.getOr("PrivateKey");
			if (privateKey.isPresent() && hasPrivateKey() && !privateKey.get().equals(PRIVATE_KEY_NOT_AVAILABLE)) {
				/*
				 * TODO private key should be removed from server at this point
				 */
				setUserPrivateKey(privateKey.get());
				setUserPublicKey(Keys.pubkeyBase64(privateKey.get()).getBase64PublicKey());
			} else if (!hasPrivateKey()) {
				throw new IllegalStateException(
						"Did not receive private key from server, and we didn't generate one on the client. Connection impossible.");
			}
			setPreUp(interfaceSection.contains("PreUp") ?  String.join("\n", interfaceSection.getAll("PreUp")) : "");
			setPostUp(interfaceSection.contains("PostUp") ? String.join("\n", interfaceSection.getAll("PostUp")) : "");
			setPreDown(interfaceSection.contains("PreDown") ? String.join("\n", interfaceSection.getAll("PreDown")) : "");
			setPostDown(interfaceSection.contains("PostDown") ? String.join("\n", interfaceSection.getAll("PostDown")) : "");
	
			/* Custom */
			ini.sectionOr("LogonBox").ifPresent(l -> {
                setRouteAll(l.getBoolean("RouteAll"));
                setSerial(l.get("Serial", null));
			});
	
			/* Peer (them) */
			var peerSection = ini.section("Peer");
			setPublicKey(peerSection.get("PublicKey"));
			String endpoint = peerSection.get("Endpoint");
			int idx = endpoint.lastIndexOf(':');
			setEndpointAddress(endpoint.substring(0, idx));
			setEndpointPort(Integer.parseInt(endpoint.substring(idx + 1)));
			setPersistentKeepalive(peerSection.getInt("PersistentKeepalive", 0));
			setAllowedIps(peerSection.getAllElse("AllowedIPs", new String[0]));
			
			return "";
		}
		catch(IOException | ParseException ioe) {
		    log.error("Failed to parse INI file.", ioe);
			return ioe.getMessage();
		}
		
	}

    @Override
    public String[] getAuthMethods() {
        assertRegistered();
        assertOwner();
        return Arrays.asList(connection.getAuthMethods()).stream().map(AuthMethod::toString).toList().toArray(new String[0]);
    }

	@Override
	public void authorize() {
		assertRegistered();
        assertOwner();
		ctx.getClientService().requestAuthorize(connection);
	}

	@Override
	public void authorized() {
		assertRegistered();
        assertOwner();
		ctx.getClientService().authorized(connection);
	}

	@Override
	public void connect() {
		assertRegistered();
        assertOwner();
		ctx.getClientService().connect(connection);
	}

	@Override
	public void deauthorize() {
		assertRegistered();
        assertOwner();
		ctx.getClientService().deauthorize(connection);
	}

	@Override
	public void delete() {
		assertRegistered();
        assertOwner();
		ctx.getClientService().delete(connection);
	}
 
	@Override
	public void disconnect(String reason) {
		assertRegistered();
        assertOwner();
		ctx.getClientService().disconnect(connection, reason);
	}

	@Override
	public String getAddress() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getAddress(), "");
	}

	@Override
	public String getMode() {
		assertRegistered();
        assertOwner();
		return connection.getMode().name();
	}

	@Override
	public String[] getAllowedIps() {
		assertRegistered();
        assertOwner();
		return connection.getAllowedIps().toArray(new String[0]);
	}

	@Override
	public String getDisplayName() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getDisplayName(), "");
	}

	@Override
	public String getDefaultDisplayName() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getDefaultDisplayName(), "");
	}

	@Override
	public String[] getDns() {
		assertRegistered();
        assertOwner();
		return connection.getDns().toArray(new String[0]);
	}

	@Override
	public String getEndpointAddress() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getEndpointAddress(), "");
	}

	@Override
	public int getEndpointPort() {
		assertRegistered();
        assertOwner();
		return connection.getEndpointPort();
	}

	@Override
	public String getHostname() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getHostname(), "");
	}

	@Override
	public long getId() {
		return connection.getId();
	}

	@Override
	public int getMtu() {
		assertRegistered();
        assertOwner();
		return connection.getMtu();
	}

	@Override
	public String getName() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getName(), "");
	}

	@Override
	public String getObjectPath() {
		return "/com/logonbox/vpn/" + connection.getId();
	}

	@Override
	public String getPath() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getPath(), "");
	}

	@Override
	public int getPersistentKeepalive() {
		assertRegistered();
        assertOwner();
		return connection.getPersistentKeepalive();
	}

	@Override
	public int getPort() {
		assertRegistered();
        assertOwner();
		return connection.getPort();
	}

	@Override
	public String getPublicKey() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getPublicKey(), "");
	}

    @Override
    public String getInstance() {
        assertRegistered();
        assertOwner();
        return Utils.defaultIfBlank(connection.getInstance(), "");
    }

    @Override
    public String getSerial() {
        assertRegistered();
        assertOwner();
        return Utils.defaultIfBlank(connection.getSerial(), "");
    }

	@Override
	public String getStatus() {
		assertRegistered();
        assertOwner();
		return ctx.getClientService().getStatusType(connection).name();
	}

	@Override
	public String getInterfaceName() {
		assertRegistered(); 
        assertOwner();
		return ctx.getClientService().getStatus(getId()).getDetail().interfaceName();
	}

	@Override
	public String getUri(boolean withUsername) {
		assertRegistered();
        assertOwner();
		return connection.getUri(withUsername);
	}

    @Override
    public String getBaseUri() {
        assertRegistered();
        assertOwner();
        return connection.getBaseUri();
    }

    @Override
    public String getClient() {
        assertRegistered();
        assertOwner();
        return connection.getClient();
    }

    @Override
    public String getApiUri() {
        assertRegistered();
        assertOwner();
        return connection.getApiUri();
    }

	@Override
	public String getConnectionTestUri(boolean withUsername) {
		assertRegistered();
        assertOwner();
		return connection.getConnectionTestUri(withUsername);
	}

	@Override
	public String getUsernameHint() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getUsernameHint(), "");
	}

	@Override
	public String getUserPublicKey() {
		assertRegistered();
        assertOwner();
		return Utils.defaultIfBlank(connection.getUserPublicKey(), "");
	}

	@Override
	public boolean hasPrivateKey() {
		assertRegistered();
        assertOwner();
		return connection.getUserPrivateKey() != null && connection.getUserPrivateKey().length() > 0;
	}

	@Override
	public boolean isAuthorized() {
		assertRegistered();
        assertOwner();
		return connection.isAuthorized();
	}

	@Override
	public boolean isConnectAtStartup() {
		assertRegistered();
        assertOwner();
		return connection.isConnectAtStartup();
	}

	public boolean isRemote() {
		return true;
	}

	@Override
	public void setAsFavourite() {
		assertRegistered();
        assertOwner();
		ctx.getClientService().setValue(getCurrentUser(), ConfigurationItem.FAVOURITE, connection.getId());
		
	}

	@Override
	public boolean isFavourite() {
		assertRegistered();
        assertOwner();
		return  ctx.getClientService().getConnections(getCurrentUser()).size() == 1 ||  ctx.getClientService().getValue(getCurrentUser(), ConfigurationItem.FAVOURITE).longValue() == connection.getId();
	}

	@Override
	public boolean isTransient() {
		assertRegistered();
        assertOwner();
		return connection.isTransient();
	}

	@Override
	public long save() {
		assertRegistered();
        assertOwner();
		return ctx.getClientService().save(connection).getId();
	}

	@Override
	public void setAddress(String address) {
		assertRegistered();
        assertOwner();
		connection.setAddress(address);
	}

	@Override
	public void setAllowedIps(String[] allowedIps) {
		assertRegistered();
        assertOwner();
		connection.setAllowedIps(Arrays.asList(allowedIps));
	}

	@Override
	public void setConnectAtStartup(boolean connectAtStartup) {
		assertRegistered();
        assertOwner();
		connection.setConnectAtStartup(connectAtStartup);
	}

	@Override
	public void setDns(String[] dns) {
		assertRegistered();
        assertOwner();
		connection.setDns(Arrays.asList(dns));
	}

	@Override
	public void setEndpointAddress(String endpointAddress) {
		assertRegistered();
        assertOwner();
		connection.setEndpointAddress(endpointAddress);
	}

	@Override
	public void setEndpointPort(int endpointPort) {
		assertRegistered();
        assertOwner();
		connection.setEndpointPort(endpointPort);
	}

	@Override
	public void setHostname(String host) {
		assertRegistered();
        assertOwner();
		connection.setHostname(host);
		ConnectionUtil.setLastKnownServerIpAddress(connection);
	}

	@Override
	public void setName(String name) {
		assertRegistered();
        assertOwner();
		connection.setName(Utils.isBlank(name) ? null : name);
	}

    @Override
    public void setSerial(String serial) {
        assertRegistered();
        assertOwner();
        connection.setSerial(Utils.isBlank(serial) ? null : serial);
    }

	@Override
	public void setPersistentKeepalive(int peristentKeepalive) {
		assertRegistered();
        assertOwner();
		connection.setPersistentKeepalive(peristentKeepalive);
	}

	@Override
	public void setPort(int port) {
		assertRegistered();
        assertOwner();
		connection.setPort(port);
	}

	@Override
	public void setPublicKey(String publicKey) {
		assertRegistered();
        assertOwner();
		connection.setPublicKey(publicKey);
	}

	@Override
	public void setUsernameHint(String usernameHint) {
		assertRegistered();
        assertOwner();
		connection.setUsernameHint(usernameHint);
	}

	@Override
	public void setUserPrivateKey(String privateKey) {
		assertRegistered();
        assertOwner();
		connection.setUserPrivateKey(privateKey);
	}

	@Override
	public void setUserPublicKey(String publicKey) {
		assertRegistered();
        assertOwner();
		connection.setUserPublicKey(publicKey);
	}

	@Override
	public void update(String name, String uri, boolean connectAtStartup, boolean stayConnected) {
		assertRegistered();
        assertOwner();
		connection.setName(name.equals("") ? null : name);
		connection.setConnectAtStartup(connectAtStartup);
		connection.setStayConnected(stayConnected);
		connection.updateFromUri(uri);
		if(!isTransient())
			save();
	}

	@Override
	public void setPath(String path) {
		assertRegistered();
        assertOwner();
		connection.setPath(path);		
	}

	@Override
	public boolean isShared() {
        assertRegistered();
        assertOwner();
		return connection.isShared();
	}

	@Override
	public String getOwner() {
        assertRegistered();
        assertOwner();
		return calcOwner();
	}

	@Override
	public void setOwner(String owner) {
        assertRegistered();
        assertOwner();
		connection.setOwner(owner.equals("") ? null : owner);		
	}

	@Override
	public void setShared(boolean shared) {
        assertRegistered();
        assertOwner();
		connection.setShared(shared);		
	}

	@Override
	public void setPreUp(String preUp) {
        assertRegistered();
        assertOwner();
		connection.setPreUp(preUp);		
	}

	@Override
	public void setPostUp(String postUp) {
        assertRegistered();
        assertOwner();
		connection.setPostUp(postUp);				
	}

	@Override
	public void setPreDown(String preDown) {
        assertRegistered();
        assertOwner();
		connection.setPreDown(preDown);		
	}

	@Override
	public void setPostDown(String postDown) {
        assertRegistered();
        assertOwner();
		connection.setPostDown(postDown);		
	}

	@Override
	public void setRouteAll(boolean routeAll) {
        assertRegistered();
        assertOwner();
		connection.setRouteAll(routeAll);		
	}

	@Override
	public boolean isRouteAll() {
        assertRegistered();
        assertOwner();
		return connection.isRouteAll();
	}

	@Override
	public String getPreUp() {
        assertRegistered();
        assertOwner();
		return connection.getPreUp();
	}

	@Override
	public String getPostUp() {
        assertRegistered();
        assertOwner();
		return connection.getPostUp();
	}

	@Override
	public String getPreDown() {
        assertRegistered();
        assertOwner();
		return connection.getPreDown();
	}

	@Override
	public String getPostDown() {
        assertRegistered();
        assertOwner();
		return connection.getPostDown();
	}

	@Override
	public long getLastHandshake() {
		assertRegistered();
        assertOwner();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().lastHandshake().toEpochMilli();
	}

	@Override
	public long getRx() {
		assertRegistered();
        assertOwner();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().rx();
	}

	@Override
	public long getTx() {
		assertRegistered();
        assertOwner();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().tx();
	}

	@Override
	public boolean isStayConnected() {
		assertRegistered();
        assertOwner();
		return connection.isStayConnected();
	}

	@Override
	public void setStayConnected(boolean stayConnected) {
		assertRegistered();
        assertOwner();
		connection.setStayConnected(stayConnected);
	}

	@Override
	public boolean isTemporarilyOffline() {
		return ctx.getClientService().getStatus(connection.getId()).getStatus() == ConnectionStatus.Type.TEMPORARILY_OFFLINE;
	}

    @Override
    public boolean isBlocked() {
        return ctx.getClientService().getStatus(connection.getId()).getStatus() == ConnectionStatus.Type.BLOCKED;
    }

	@Override
	public String getLastError() {
		assertRegistered();
        assertOwner();
		var status = ctx.getClientService().getStatus(connection.getId());
		var detail = status.getDetail();
		if(detail != null && detail.error().isPresent())
			return detail.error().get();
		else
			return Utils.defaultIfBlank(status.getConnection().getError(), "");
	}

	@Override
	public String getAuthorizeUri() {
		assertRegistered();
        assertOwner();
		return getUri(false) + ctx.getClientService().getStatus(connection.getId()).getAuthorizeUri();
	}
	
	private void assertOwner() {
	    if(getSourceOrDefault().equals(DIRECT)) {
	        return;
	    }
        if (!connection.isShared() && !getCurrentUser().equals(calcOwner())) {
            throw new IllegalStateException("Not owner.");
        }
	}

    private String calcOwner() {
        return connection.getOwner() == null ? "" : connection.getOwner();
    }

}
