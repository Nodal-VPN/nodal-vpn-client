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
package com.logonbox.vpn.client.ini;

import static com.jadaptive.nodal.core.lib.util.Util.isBlank;

import com.jadaptive.nodal.core.lib.VpnPeer;
import com.jadaptive.nodal.core.lib.util.Keys;
import com.jadaptive.nodal.core.lib.util.Util;
import com.logonbox.vpn.client.common.AuthMethod;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.Utils;
import com.sshtools.jini.INI;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.MultiValueMode;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.INIWriter.StringQuoteMode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ConnectionImpl implements Connection, Serializable {

	private static final long serialVersionUID = 1007856764641094257L;

	private Long id;
    private String instance = UUID.randomUUID().toString();
	private String usernameHint;
	private String userPrivateKey;
	private String userPublicKey;
	private String publicKey;
    private String presharedKey;
	private String address;
    private String serial;
	private String endpointAddress;
	private int endpointPort;
	private int mtu;
    private int fwMark;
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
    private String table;
    private boolean saveConfig;
    private AuthMethod[] authMethods = new AuthMethod[0];

    private final ConnectionImplPeer peer;

    @Override
    public AuthMethod[] getAuthMethods() {
        return authMethods;
    }

    @Override
    public void setAuthMethods(AuthMethod[] authMethods) {
        this.authMethods = authMethods;
    }

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
    public String getInstance() {
        return instance;
    }

    @Override
    public void setInstance(String instance) {
        this.instance = instance;
    }

	public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
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

    @Override
    public String getPresharedKey() {
        return presharedKey;
    }

    @Override
    public void setPresharedKey(String presharedKey) {
        this.presharedKey = presharedKey;
    }

    @Override
    public int getFwMark() {
        return fwMark;
    }

    @Override
    public void setFwMark(int fwMark) {
        this.fwMark = fwMark;
    }

    @Override
	public int hashCode() {
		return Objects.hash(id);
	}

    @Override
	public String getTable() {
        return table;
    }

    @Override
	public void setTable(String table) {
        this.table = table;
    }

    @Override
    public boolean isSaveConfig() {
        return saveConfig;
    }

    @Override
    public void setSaveConfig(boolean saveConfig) {
        this.saveConfig = saveConfig;
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
        peer = new ConnectionImplPeer();
	}

	public ConnectionImpl(Long id, BufferedReader r) throws IOException {
	    this();
		this.id = id;

		var rdr = new INIReader.Builder().
	            withCommentCharacter('#').
                withMultiValueMode(MultiValueMode.SEPARATED).
		        build();
		try {
    		var ini = rdr.read(r);
    
    		/* Interface (us) */
    		var interfaceSection = ini.section("Interface");
    		setAddress(interfaceSection.getOr("Address").orElse(null));
            setFwMark(interfaceSection.getIntOr("FwMark").orElse(0));
    		setDns(Arrays.asList(interfaceSection.getAllElse("DNS", new String[0])));
            setTable(interfaceSection.getOr("Table").orElse(null));
            setSaveConfig(interfaceSection.getBooleanOr("SaveConfig").orElse(false));
    
    		String privateKey = interfaceSection.get("PrivateKey");
    		setUserPrivateKey(privateKey);
    		setUserPublicKey(Keys.pubkeyBase64(privateKey).getBase64PublicKey());
    		setPreUp(interfaceSection.contains("PreUp") ? String.join("\n", interfaceSection.getAll("PreUp")) : "");
    		setPostUp(interfaceSection.contains("PostUp") ? String.join("\n", interfaceSection.getAll("PostUp")) : "");
    		setPreDown(
    				interfaceSection.contains("PreDown") ? String.join("\n", interfaceSection.getAll("PreDown")) : "");
    		setPostDown(
    				interfaceSection.contains("PostDown") ? String.join("\n", interfaceSection.getAll("PostDown")) : "");
    
    		/* Custom */
    		ini.sectionOr("LogonBox").ifPresent(l -> {
                setRouteAll(l.getBoolean("RouteAll", false));
                setShared(l.getBoolean("Shared", false));
                setConnectAtStartup(l.getBoolean("ConnectAtStartup", false));
                setStayConnected(l.getBoolean("StayConnected", false));
                var serial = l.get("Serial", "");
                if(serial.length() > 0)
                    setSerial(serial);
                setMode(Mode.valueOf(l.get("Mode")));
                l.getAllOr("AuthMethods").ifPresent(all -> {
                    setAuthMethods(Arrays.asList(all).stream().map(AuthMethod::valueOf).toList().toArray(new AuthMethod[0]));
                });
                setOwner(l.get("Owner", null));
                setUsernameHint(l.get("UsernameHint", null));
                setHostname(l.get("Hostname", null));
                setPath(l.get("Path", null));
                setError(l.get("Error", null));
                setName(l.get("Name", null));
                setPort(l.getInt("Port"));
                l.getIntOr("MTU").ifPresent(this::setMtu);
                setLastKnownServerIpAddress(l.get("LastKnownServerIpAddress", null));
                setInstance(l.get("Instance", UUID.randomUUID().toString()));
    		});
    
    		/* Peer (them) */
    		ini.sectionOr("Peer").ifPresent(p -> {
                setPublicKey(p.get("PublicKey"));
                p.getOr("Endpoint").ifPresent(endpoint -> {
                    int idx = endpoint.lastIndexOf(':');
                    setEndpointAddress(endpoint.substring(0, idx));
                    setEndpointPort(Integer.parseInt(endpoint.substring(idx + 1)));
                });
                setPresharedKey(p.get("PresharedKey", null));
                setPersistentKeepalive(p.getInt("PersistentKeepalive"));
                setAllowedIps(Arrays.asList(p.getAllElse("AllowedIPs", new String[0])));
    		});
		}
		catch(ParseException pe) {
		    throw new IOException("Failed to parse.", pe);
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

	public void setPersistentKeepalive(int peristentKeepalive) {
		this.peristentKeepalive = peristentKeepalive;
	}

	@Override
	public String toString() {
		return "ConnectionImpl [id=" + id + ", userPublicKey=" + userPublicKey
				+ ", publicKey=" + publicKey + ", address=" + address + ", endpointAddress=" + endpointAddress
				+ ", endpointPort=" + endpointPort + ", dns=" + dns + ", stayConnected=" + stayConnected
				 + ", connectAtStartup=" + connectAtStartup + ", peristentKeepalive=" + peristentKeepalive
				+ ", allowedIps=" + allowedIps + ", lastKnownServerIpAddress=" + lastKnownServerIpAddress + "]";
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
		return Utils.isNotBlank(endpointAddress);
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
		var ini = INI.create();

		/* Interface (us) */
		var interfaceSection = ini.create("Interface");
		interfaceSection.put("Address", connection.getAddress());
		if (!connection.getDns().isEmpty())
			interfaceSection.putAll("DNS", connection.getDns().toArray(new String[0]));
		interfaceSection.put("PrivateKey", connection.getUserPrivateKey());
		if (Utils.isNotBlank(connection.getPreUp()))
			interfaceSection.putAll("PreUp", connection.getPreUp().split("\\n"));
		if (Utils.isNotBlank(connection.getPostUp()))
			interfaceSection.putAll("PostUp", connection.getPostUp().split("\\n"));
		if (Utils.isNotBlank(connection.getPreDown()))
			interfaceSection.putAll("PreDown", connection.getPreDown().split("\\n"));
		if (Utils.isNotBlank(connection.getPostDown()))
			interfaceSection.putAll("PostDown", connection.getPostDown().split("\\n"));
		if(connection.getFwMark() > 0)
		    interfaceSection.put("FwMark", connection.getFwMark());
		if(connection.isSaveConfig())
            interfaceSection.put("SaveConfig", connection.isSaveConfig());
		if(Utils.isNotBlank("Table"))
            interfaceSection.put("Table", connection.getTable());

		/* Custom */
		var logonBoxSection = ini.create("LogonBox");
		logonBoxSection.put("RouteAll", connection.isRouteAll());
		logonBoxSection.put("Shared", connection.isShared());
		if(connection.getSerial() != null && connection.getSerial().length() > 0) {
		    logonBoxSection.put("Serial", connection.getSerial());
		}
		logonBoxSection.put("ConnectAtStartup", connection.isConnectAtStartup());
		logonBoxSection.put("StayConnected", connection.isStayConnected());
		logonBoxSection.put("Mode", connection.getMode().name());
        if(connection.getAuthMethods().length > 0) {
            logonBoxSection.putAll("AuthMethods", 
                    Arrays.asList(connection.getAuthMethods()).stream().
                    map(AuthMethod::toString).toList().toArray(new String[0]));
        }
		if (Utils.isNotBlank(connection.getOwner()))
			logonBoxSection.put("Owner", connection.getOwner());
        if (Utils.isNotBlank(connection.getInstance()))
            logonBoxSection.put("Instance", connection.getInstance());
		if (Utils.isNotBlank(connection.getUsernameHint()))
			logonBoxSection.put("UsernameHint", connection.getUsernameHint());
		if (Utils.isNotBlank(connection.getHostname()))
			logonBoxSection.put("Hostname", connection.getHostname());
		if (Utils.isNotBlank(connection.getPath()))
			logonBoxSection.put("Path", connection.getPath());
		if (Utils.isNotBlank(connection.getError()))
			logonBoxSection.put("Error", connection.getError());
		if (Utils.isNotBlank(connection.getName()))
			logonBoxSection.put("Name", connection.getName());
		if (Utils.isNotBlank(connection.getLastKnownServerIpAddress()))
			logonBoxSection.put("LastKnownServerIpAddress", connection.getLastKnownServerIpAddress());
		logonBoxSection.put("Port", connection.getPort());
		if (connection.getMtu() > 0)
			logonBoxSection.put("MTU", connection.getMtu());

		/* Peer (them) */
		if (Utils.isNotBlank(connection.getPublicKey())) {
			var peerSection = ini.create("Peer");
			peerSection.put("PublicKey", connection.getPublicKey());
			peerSection.put("Endpoint", connection.getEndpointAddress() + ":" + connection.getEndpointPort());
			peerSection.put("PersistentKeepalive", connection.getPersistentKeepalive());
			if (!connection.getAllowedIps().isEmpty())
				peerSection.putAll("AllowedIPs", connection.getAllowedIps().toArray(new String[0]));
			if(Utils.isNotBlank(connection.getPresharedKey()))
			    peerSection.put("PresharedKey", connection.getPresharedKey());
			    
		}

		new INIWriter.Builder().
		    withEmptyValues(false).
		    withCommentCharacter('#').
		    withStringQuoteMode(StringQuoteMode.NEVER).
            withMultiValueMode(MultiValueMode.SEPARATED).
		    build().write(ini, w);
	}

    @Override
    public Optional<Integer> listenPort() {
        return Optional.empty();
    }

    @Override
    public String publicKey() {
        return getUserPublicKey();
    }

    @Override
    public String privateKey() {
        return getUserPrivateKey();
    }

    @Override
    public List<String> dns() {
        return getDns();
    }

    @Override
    public Optional<Integer> fwMark() {
        return fwMark == 0 ? Optional.empty() : Optional.of(fwMark);
    }

    @Override
    public Optional<Integer> mtu() {
        return getMtu() == 0 ? Optional.empty() : Optional.of(getMtu());
    }

    @Override
    public List<String> addresses() {
        var addr = getAddress();
        return isBlank(addr) ? Collections.emptyList() : Arrays.asList(addr);
    }

    @Override
    public String[] preUp() {
        return isBlank(preUp) ? new String[0] : preUp.split("\\n");
    }

    @Override
    public String[] postUp() {
        return isBlank(postUp) ? new String[0] : postUp.split("\\n");
    }

    @Override
    public String[] preDown() {
        return isBlank(preDown) ? new String[0] : preDown.split("\\n");
    }

    @Override
    public String[] postDown() {
        return isBlank(postDown) ? new String[0] : postDown.split("\\n");
    }

    @Override
    public List<VpnPeer> peers() {
        if(Utils.isNotBlank(getPublicKey())) {
            return Arrays.asList(peer);
        }
        else
            return Collections.emptyList();
    }
    
    @Override
    public Optional<String> table() {
        return table == null || table.equals("") ? Optional.empty() : Optional.of(table);
    }

    @Override
    public boolean saveConfig() {
        return saveConfig;
    }

    @SuppressWarnings("serial")
    class ConnectionImplPeer implements VpnPeer {

        @Override
        public Optional<String> endpointAddress() {
            return Util.isBlank(endpointAddress) ? Optional.empty() : Optional.of(endpointAddress); 
        }

        @Override
        public Optional<Integer> endpointPort() {
            return getEndpointPort() == 0 ? Optional.empty() : Optional.of(getEndpointPort());
        }

        @Override
        public Optional<Integer> persistentKeepalive() {
            return getPersistentKeepalive() == 0 ? Optional.empty() : Optional.of(getPersistentKeepalive());
        }

        @Override
        public List<String> allowedIps() {
            return getAllowedIps();
        }

        @Override
        public String publicKey() {
            return ConnectionImpl.this.getPublicKey();
        }

        @Override
        public Optional<String> presharedKey() {
            return Optional.ofNullable(presharedKey);
        }
    }

}
