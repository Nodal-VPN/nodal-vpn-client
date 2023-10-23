package com.logonbox.vpn.client.desktop.service.dbus;

import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionUtil;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.dbus.VPNConnection;
import com.logonbox.vpn.client.desktop.service.DesktopServiceContext;
import com.logonbox.vpn.drivers.lib.util.Keys;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.MultiValueMode;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;

@DBusInterfaceName("com.logonbox.vpn.Connection")
public class VPNConnectionImpl extends AbstractVPNComponent implements VPNConnection {
    static Logger log = LoggerFactory.getLogger(VPNConnectionImpl.class);

	private Connection connection;
	private DesktopServiceContext ctx;

	public VPNConnectionImpl(DesktopServiceContext ctx, Connection connection) {
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
			setDns(interfaceSection.getAllOr("DNS", new String[0]));
	
			String privateKey = interfaceSection.get("PrivateKey");
			if (privateKey != null && hasPrivateKey() && !privateKey.equals(PRIVATE_KEY_NOT_AVAILABLE)) {
				/*
				 * TODO private key should be removed from server at this point
				 */
				setUserPrivateKey(privateKey);
				setUserPublicKey(Keys.pubkey(privateKey).getBase64PublicKey());
			} else if (!hasPrivateKey()) {
				throw new IllegalStateException(
						"Did not receive private key from server, and we didn't generate one on the client. Connection impossible.");
			}
			setPreUp(interfaceSection.contains("PreUp") ?  String.join("\n", interfaceSection.getAll("PreUp")) : "");
			setPostUp(interfaceSection.contains("PostUp") ? String.join("\n", interfaceSection.getAll("PostUp")) : "");
			setPreDown(interfaceSection.contains("PreDown") ? String.join("\n", interfaceSection.getAll("PreDown")) : "");
			setPostDown(interfaceSection.contains("PostDown") ? String.join("\n", interfaceSection.getAll("PostDown")) : "");
	
			/* Custom LogonBox */
			ini.sectionOr("LogonBox").ifPresent(l -> {
                setRouteAll(l.getBoolean("RouteAll"));
			});
	
			/* Peer (them) */
			var peerSection = ini.section("Peer");
			setPublicKey(peerSection.get("PublicKey"));
			String endpoint = peerSection.get("Endpoint");
			int idx = endpoint.lastIndexOf(':');
			setEndpointAddress(endpoint.substring(0, idx));
			setEndpointPort(Integer.parseInt(endpoint.substring(idx + 1)));
			setPersistentKeepalive(peerSection.getInt("PersistentKeepalive"));
			setAllowedIps(peerSection.getAllOr("AllowedIPs", new String[0]));
			
			return "";
		}
		catch(IOException | ParseException ioe) {
		    log.error("Failed to parse INI file.", ioe);
			return ioe.getMessage();
		}
		
	}

	@Override
	public void authorize() {
		assertRegistered();
		ctx.getClientService().requestAuthorize(connection);
	}

	@Override
	public void authorized() {
		assertRegistered();
		ctx.getClientService().authorized(connection);
	}

	@Override
	public void connect() {
		assertRegistered();
		ctx.getClientService().connect(connection);
	}

	@Override
	public void deauthorize() {
		assertRegistered();
		ctx.getClientService().deauthorize(connection);
	}

	@Override
	public void delete() {
		assertRegistered();
		ctx.getClientService().delete(connection);
	}

	@Override
	public void disconnect(String reason) {
		assertRegistered();
		ctx.getClientService().disconnect(connection, reason);
	}

	@Override
	public String getAddress() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getAddress(), "");
	}

	@Override
	public String getMode() {
		assertRegistered();
		return connection.getMode().name();
	}

	@Override
	public String[] getAllowedIps() {
		assertRegistered();
		return connection.getAllowedIps().toArray(new String[0]);
	}

	@Override
	public String getDisplayName() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getDisplayName(), "");
	}

	@Override
	public String getDefaultDisplayName() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getDefaultDisplayName(), "");
	}

	@Override
	public String[] getDns() {
		assertRegistered();
		return connection.getDns().toArray(new String[0]);
	}

	@Override
	public String getEndpointAddress() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getEndpointAddress(), "");
	}

	@Override
	public int getEndpointPort() {
		assertRegistered();
		return connection.getEndpointPort();
	}

	@Override
	public String getHostname() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getHostname(), "");
	}

	@Override
	public long getId() {
		assertRegistered();
		return connection.getId();
	}

	@Override
	public int getMtu() {
		assertRegistered();
		return connection.getMtu();
	}

	@Override
	public String getName() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getName(), "");
	}

	@Override
	public String getObjectPath() {
		return "/com/logonbox/vpn/" + connection.getId();
	}

	@Override
	public String getPath() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getPath(), "");
	}

	@Override
	public int getPersistentKeepalive() {
		assertRegistered();
		return connection.getPersistentKeepalive();
	}

	@Override
	public int getPort() {
		assertRegistered();
		return connection.getPort();
	}

	@Override
	public String getPublicKey() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getPublicKey(), "");
	}

	@Override
	public String getStatus() {
		assertRegistered();
		return ctx.getClientService().getStatusType(connection).name();
	}

	@Override
	public String getInterfaceName() {
		assertRegistered(); 
		return ctx.getClientService().getStatus(getId()).getDetail().interfaceName();
	}

	@Override
	public String getUri(boolean withUsername) {
		assertRegistered();
		return connection.getUri(withUsername);
	}

	@Override
	public String getConnectionTestUri(boolean withUsername) {
		assertRegistered();
		return connection.getConnectionTestUri(withUsername);
	}

	@Override
	public String getUsernameHint() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getUsernameHint(), "");
	}

	@Override
	public String getUserPublicKey() {
		assertRegistered();
		return Utils.defaultIfBlank(connection.getUserPublicKey(), "");
	}

	@Override
	public boolean hasPrivateKey() {
		assertRegistered();
		return connection.getUserPrivateKey() != null && connection.getUserPrivateKey().length() > 0;
	}

	@Override
	public boolean isAuthorized() {
		assertRegistered();
		return connection.isAuthorized();
	}

	@Override
	public boolean isConnectAtStartup() {
		assertRegistered();
		return connection.isConnectAtStartup();
	}

	public boolean isRemote() {
		return true;
	}

	@Override
	public void setAsFavourite() {
		assertRegistered();
		ctx.getClientService().setValue(getOwner(), ConfigurationItem.FAVOURITE, connection.getId());
		
	}

	@Override
	public boolean isFavourite() {
		assertRegistered();
		return  ctx.getClientService().getConnections(getOwner()).size() == 1 ||  ctx.getClientService().getValue(getOwner(), ConfigurationItem.FAVOURITE).longValue() == connection.getId();
	}

	@Override
	public boolean isTransient() {
		assertRegistered();
		return connection.isTransient();
	}

	@Override
	public long save() {
		assertRegistered();
		return ctx.getClientService().save(connection).getId();
	}

	@Override
	public void setAddress(String address) {
		assertRegistered();
		connection.setAddress(address);
	}

	@Override
	public void setAllowedIps(String[] allowedIps) {
		assertRegistered();
		connection.setAllowedIps(Arrays.asList(allowedIps));
	}

	@Override
	public void setConnectAtStartup(boolean connectAtStartup) {
		assertRegistered();
		connection.setConnectAtStartup(connectAtStartup);
	}

	@Override
	public void setDns(String[] dns) {
		assertRegistered();
		connection.setDns(Arrays.asList(dns));
	}

	@Override
	public void setEndpointAddress(String endpointAddress) {
		assertRegistered();
		connection.setEndpointAddress(endpointAddress);
	}

	@Override
	public void setEndpointPort(int endpointPort) {
		assertRegistered();
		connection.setEndpointPort(endpointPort);
	}

	@Override
	public void setHostname(String host) {
		assertRegistered();
		connection.setHostname(host);
		ConnectionUtil.setLastKnownServerIpAddress(connection);
	}

	@Override
	public void setName(String name) {
		assertRegistered();
		connection.setName(Utils.isBlank(name) ? null : name);
	}

	@Override
	public void setPersistentKeepalive(int peristentKeepalive) {
		assertRegistered();
		connection.setPeristentKeepalive(peristentKeepalive);
	}

	@Override
	public void setPort(int port) {
		assertRegistered();
		connection.setPort(port);
	}

	@Override
	public void setPublicKey(String publicKey) {
		assertRegistered();
		connection.setPublicKey(publicKey);
	}

	@Override
	public void setUsernameHint(String usernameHint) {
		assertRegistered();
		connection.setUsernameHint(usernameHint);
	}

	@Override
	public void setUserPrivateKey(String privateKey) {
		assertRegistered();
		connection.setUserPrivateKey(privateKey);
	}

	@Override
	public void setUserPublicKey(String publicKey) {
		assertRegistered();
		connection.setUserPublicKey(publicKey);
	}

	@Override
	public void update(String name, String uri, boolean connectAtStartup, boolean stayConnected) {
		assertRegistered();
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
		connection.setPath(path);		
	}

	@Override
	public boolean isShared() {
		return connection.isShared();
	}

	@Override
	public String getOwner() {
		return connection.getOwner() == null ? "" : connection.getOwner();
	}

	@Override
	public void setOwner(String owner) {
		connection.setOwner(owner.equals("") ? null : owner);		
	}

	@Override
	public void setShared(boolean shared) {
		connection.setShared(shared);		
	}

	@Override
	public void setPreUp(String preUp) {
		connection.setPreUp(preUp);		
	}

	@Override
	public void setPostUp(String postUp) {
		connection.setPostUp(postUp);				
	}

	@Override
	public void setPreDown(String preDown) {
		connection.setPreDown(preDown);		
	}

	@Override
	public void setPostDown(String postDown) {
		connection.setPostDown(postDown);		
	}

	@Override
	public void setRouteAll(boolean routeAll) {
		connection.setRouteAll(routeAll);		
	}

	@Override
	public boolean isRouteAll() {
		return connection.isRouteAll();
	}

	@Override
	public String getPreUp() {
		return connection.getPreUp();
	}

	@Override
	public String getPostUp() {
		return connection.getPostUp();
	}

	@Override
	public String getPreDown() {
		return connection.getPreDown();
	}

	@Override
	public String getPostDown() {
		return connection.getPostDown();
	}

	@Override
	public long getLastHandshake() {
		assertRegistered();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().lastHandshake().toEpochMilli();
	}

	@Override
	public long getRx() {
		assertRegistered();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().rx();
	}

	@Override
	public long getTx() {
		assertRegistered();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().tx();
	}

	@Override
	public boolean isStayConnected() {
		assertRegistered();
		return connection.isStayConnected();
	}

	@Override
	public void setStayConnected(boolean stayConnected) {
		assertRegistered();
		connection.setStayConnected(stayConnected);
	}

	@Override
	public boolean isTemporarilyOffline() {
		return ctx.getClientService().getStatus(connection.getId()).getStatus() == ConnectionStatus.Type.TEMPORARILY_OFFLINE;
	}

	@Override
	public String getLastError() {
		assertRegistered();
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
		return getUri(false) + ctx.getClientService().getStatus(connection.getId()).getAuthorizeUri();
	}

}
