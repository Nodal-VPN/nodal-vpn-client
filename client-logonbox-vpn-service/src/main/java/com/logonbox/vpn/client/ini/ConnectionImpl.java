package com.logonbox.vpn.client.ini;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.Keys;
import com.logonbox.vpn.common.client.Util;

public class ConnectionImpl implements Connection, Serializable {

	private static final long serialVersionUID = 1007856764641094257L;

	private Long id;
	private String usernameHint;
	private String userPrivateKey;
	private String userPublicKey;
	private String publicKey;
	private String address;
	private String endpointAddress;
	private int endpointPort;
	private int mtu;
	private String dns;
	private int peristentKeepalive;
	private boolean shared;
	private String owner;
	private String allowedIps;
	private String name;
	private String hostname;
	private Integer port = Integer.valueOf(443);
	private String path = "/app";
	private Mode mode = Mode.PEER;
	private boolean stayConnected;
	private boolean connectAtStartup;
	private boolean routeAll;
	private String preUp;
	private String postUp;
	private String preDown;
	private String postDown;
	private String error;
	private String lastKnownServerIpAddress;

	@Override
	public void setLastKnownServerIpAddress(String lastKnownServerIpAddress) {
		this.lastKnownServerIpAddress = lastKnownServerIpAddress;
	}

	@Override
	public String getLastKnownServerIpAddress() {
		return lastKnownServerIpAddress;
	}

	@Override
	public boolean isRouteAll() {
		return routeAll;
	}

	@Override
	public void setRouteAll(boolean routeAll) {
		this.routeAll = routeAll;
	}

	@Override
	public Mode getMode() {
		return mode;
	}

	@Override
	public void setMode(Mode mode) {
		this.mode = mode;
	}

	@Override
	public String getPreUp() {
		return preUp;
	}

	@Override
	public void setPreUp(String preUp) {
		this.preUp = preUp;
	}

	@Override
	public String getPostUp() {
		return postUp;
	}

	@Override
	public void setPostUp(String postUp) {
		this.postUp = postUp;
	}

	@Override
	public String getPreDown() {
		return preDown;
	}

	@Override
	public void setPreDown(String preDown) {
		this.preDown = preDown;
	}

	@Override
	public String getPostDown() {
		return postDown;
	}

	@Override
	public void setPostDown(String postDown) {
		this.postDown = postDown;
	}

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public String getOwner() {
		return owner;
	}

	@Override
	public void setOwner(String owner) {
		this.owner = owner;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getHostname() {
		return hostname;
	}

	@Override
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public boolean isStayConnected() {
		return stayConnected;
	}

	@Override
	public void setStayConnected(boolean stayConnected) {
		this.stayConnected = stayConnected;
	}

	@Override
	public boolean isConnectAtStartup() {
		return connectAtStartup;
	}

	@Override
	public void setConnectAtStartup(boolean connectAtStartup) {
		this.connectAtStartup = connectAtStartup;
	}

	@Override
	public void setPort(Integer port) {
		this.port = port;
	}

	@Override
	public boolean isShared() {
		return shared;
	}

	@Override
	public void setShared(boolean publicToAll) {
		this.shared = publicToAll;
	}

//	@Override
//	public int hashCode() {
//		return Objects.hash(hostname, owner, path, port);
//	}
//
//	@Override
//	public boolean equals(Object obj) {
//		if (this == obj)
//			return true;
//		if (obj == null)
//			return false;
//		if (getClass() != obj.getClass())
//			return false;
//		ConnectionImpl other = (ConnectionImpl) obj;
//		return Objects.equals(hostname, other.hostname) && Objects.equals(owner, other.owner)
//				&& Objects.equals(path, other.path) && Objects.equals(port, other.port);
//	}

	
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConnectionImpl other = (ConnectionImpl) obj;
		return Objects.equals(id, other.id);
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
	}

	public ConnectionImpl() {
	}

	public ConnectionImpl(Long id, BufferedReader r) throws IOException {
		this.id = id;

		Ini ini = new Ini(r);

		/* Interface (us) */
		Section interfaceSection = ini.get("Interface");
		setAddress(interfaceSection.get("Address"));
		setDns(Util.toStringList(interfaceSection, "DNS"));

		String privateKey = interfaceSection.get("PrivateKey");
		setUserPrivateKey(privateKey);
		setUserPublicKey(Keys.pubkey(privateKey).getBase64PublicKey());
		setPreUp(interfaceSection.containsKey("PreUp") ? String.join("\n", interfaceSection.getAll("PreUp")) : "");
		setPostUp(interfaceSection.containsKey("PostUp") ? String.join("\n", interfaceSection.getAll("PostUp")) : "");
		setPreDown(
				interfaceSection.containsKey("PreDown") ? String.join("\n", interfaceSection.getAll("PreDown")) : "");
		setPostDown(
				interfaceSection.containsKey("PostDown") ? String.join("\n", interfaceSection.getAll("PostDown")) : "");

		/* Custom LogonBox */
		Section logonBoxSection = ini.get("LogonBox");
		if (logonBoxSection != null) {
			setRouteAll("true".equals(logonBoxSection.get("RouteAll")));
			setShared("true".equals(logonBoxSection.get("Shared")));
			setConnectAtStartup("true".equals(logonBoxSection.get("ConnectAtStartup")));
			setStayConnected("true".equals(logonBoxSection.get("StayConnected")));
			setMode(Mode.valueOf(logonBoxSection.get("Mode")));
			setOwner(logonBoxSection.get("Owner"));
			setUsernameHint(logonBoxSection.get("UsernameHint"));
			setHostname(logonBoxSection.get("Hostname"));
			setPath(logonBoxSection.get("Path"));
			setError(logonBoxSection.get("Error"));
			setName(logonBoxSection.get("Name"));
			setPort(Integer.parseInt(logonBoxSection.get("Port")));
			if (logonBoxSection.containsKey("MTU"))
				setMtu(Integer.parseInt(logonBoxSection.get("MTU")));
			setLastKnownServerIpAddress(logonBoxSection.get("LastKnownServerIpAddress"));
		}

		/* Peer (them) */
		Section peerSection = ini.get("Peer");
		if (peerSection != null) {
			setPublicKey(peerSection.get("PublicKey"));
			String endpoint = peerSection.get("Endpoint");
			if(endpoint != null) {
				int idx = endpoint.lastIndexOf(':');
				setEndpointAddress(endpoint.substring(0, idx));
				setEndpointPort(Integer.parseInt(endpoint.substring(idx + 1)));
			}
			setPeristentKeepalive(Integer.parseInt(peerSection.get("PersistentKeepalive")));
			setAllowedIps(Util.toStringList(peerSection, "AllowedIPs"));
		}

	}

	public String getUsernameHint() {
		return usernameHint;
	}

	public void setUsernameHint(String usernameHint) {
		this.usernameHint = usernameHint;
	}

	public int getMtu() {
		return mtu;
	}

	public void setMtu(int mtu) {
		this.mtu = mtu;
	}

	public String getEndpointAddress() {
		return endpointAddress;
	}

	public void setEndpointAddress(String endpointAddress) {
		this.endpointAddress = endpointAddress;
	}

	public int getEndpointPort() {
		return endpointPort;
	}

	public void setEndpointPort(int endpointPort) {
		this.endpointPort = endpointPort;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUserPrivateKey() {
		return userPrivateKey;
	}

	public void setUserPrivateKey(String userPrivateKey) {
		this.userPrivateKey = userPrivateKey;
	}

	public String getUserPublicKey() {
		return userPublicKey;
	}

	public void setUserPublicKey(String userPublicKey) {
		this.userPublicKey = userPublicKey;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public List<String> getDns() {
		return dns == null || dns.length() == 0 ? Collections.emptyList() : Arrays.asList(dns.split(","));
	}

	public void setDns(List<String> dns) {
		this.dns = dns == null || dns.size() == 0 ? null : String.join(",", dns);
	}

	public int getPersistentKeepalive() {
		return peristentKeepalive;
	}

	public void setPeristentKeepalive(int peristentKeepalive) {
		this.peristentKeepalive = peristentKeepalive;
	}

	@Override
	public String toString() {
		return "ConnectionImpl [id=" + id + ", userPrivateKey=" + userPrivateKey + ", userPublicKey=" + userPublicKey
				+ ", publicKey=" + publicKey + ", address=" + address + ", endpointAddress=" + endpointAddress
				+ ", endpointPort=" + endpointPort + ", dns=" + dns + ", peristentKeepalive=" + peristentKeepalive
				+ ", allowedIps=" + allowedIps + "]";
	}

	public List<String> getAllowedIps() {
		return allowedIps == null || allowedIps.length() == 0 ? Collections.emptyList()
				: Arrays.asList(allowedIps.split(","));
	}

	public void setAllowedIps(List<String> allowedIps) {
		this.allowedIps = allowedIps == null || allowedIps.size() == 0 ? null : String.join(",", allowedIps);
	}

	@Override
	public boolean isAuthorized() {
		return StringUtils.isNotBlank(endpointAddress);
	}

	@Override
	public void deauthorize() {
		usernameHint = null;
		publicKey = null;
		address = null;
		endpointAddress = null;
		endpointPort = 0;
		mtu = 0;
		dns = null;
		peristentKeepalive = 0;
		allowedIps = null;
		error = null;
	}

	@Override
	public void setError(String error) {
		this.error = error;
	}

	@Override
	public String getError() {
		return error;
	}

	public static void write(BufferedWriter w, Connection connection) throws IOException {
		Ini ini = new Ini();

		/* Interface (us) */
		Section interfaceSection = ini.add("Interface");
		interfaceSection.put("Address", connection.getAddress());
		if (!connection.getDns().isEmpty())
			interfaceSection.put("DNS", String.join(", ", connection.getDns()));
		interfaceSection.put("PrivateKey", connection.getUserPrivateKey());
		if (StringUtils.isNotBlank(connection.getPreUp()))
			interfaceSection.put("PreUp", connection.getPreUp().split("\\n"));
		if (StringUtils.isNotBlank(connection.getPostUp()))
			interfaceSection.put("PostUp", connection.getPostUp().split("\\n"));
		if (StringUtils.isNotBlank(connection.getPreDown()))
			interfaceSection.put("PreDown", connection.getPreDown().split("\\n"));
		if (StringUtils.isNotBlank(connection.getPostDown()))
			interfaceSection.put("PostDown", connection.getPostDown().split("\\n"));

		/* Custom LogonBox */
		Section logonBoxSection = ini.add("LogonBox");
		logonBoxSection.put("RouteAll", connection.isRouteAll());
		logonBoxSection.put("Shared", connection.isShared());
		logonBoxSection.put("ConnectAtStartup", connection.isConnectAtStartup());
		logonBoxSection.put("StayConnected", connection.isStayConnected());
		logonBoxSection.put("Mode", connection.getMode().name());
		if (StringUtils.isNotBlank(connection.getOwner()))
			logonBoxSection.put("Owner", connection.getOwner());
		if (StringUtils.isNotBlank(connection.getUsernameHint()))
			logonBoxSection.put("UsernameHint", connection.getUsernameHint());
		if (StringUtils.isNotBlank(connection.getHostname()))
			logonBoxSection.put("Hostname", connection.getHostname());
		if (StringUtils.isNotBlank(connection.getPath()))
			logonBoxSection.put("Path", connection.getPath());
		if (StringUtils.isNotBlank(connection.getError()))
			logonBoxSection.put("Error", connection.getError());
		if (StringUtils.isNotBlank(connection.getName()))
			logonBoxSection.put("Name", connection.getName());
		if (StringUtils.isNotBlank(connection.getLastKnownServerIpAddress()))
			logonBoxSection.put("LastKnownServerIpAddress", connection.getLastKnownServerIpAddress());
		logonBoxSection.put("Port", connection.getPort());
		if (connection.getMtu() > 0)
			logonBoxSection.put("MTU", connection.getMtu());

		/* Peer (them) */
		if (StringUtils.isNotBlank(connection.getPublicKey())) {
			Section peerSection = ini.add("Peer");
			peerSection.put("PublicKey", connection.getPublicKey());
			peerSection.put("Endpoint", connection.getEndpointAddress() + ":" + connection.getEndpointPort());
			peerSection.put("PersistentKeepalive", connection.getPersistentKeepalive());
			if (!connection.getAllowedIps().isEmpty())
				peerSection.put("AllowedIps", String.join(", ", connection.getAllowedIps()));
		}

		ini.store(w);
	}

}
