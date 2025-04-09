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
package com.logonbox.vpn.client.embedded;

import static com.logonbox.vpn.client.common.Utils.defaultIfBlank;

import com.jadaptive.nodal.core.lib.util.Keys;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.AuthMethod;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionUtil;
import com.logonbox.vpn.client.common.Utils;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.MultiValueMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.stream.Stream;

public class EmbeddedVpnConnection implements IVpnConnection {
    static Logger log = LoggerFactory.getLogger(EmbeddedVpnConnection.class);

    private final Connection connection;
    private final LocalContext<EmbeddedVpnConnection> ctx;

    public EmbeddedVpnConnection(LocalContext<EmbeddedVpnConnection> ctx, Connection connection) {
        this.connection = connection;
        this.ctx = ctx;
    }

    @Override
    public String parse(String configIniFile) {
        try {
            var rdr = new INIReader.Builder().withMultiValueMode(MultiValueMode.SEPARATED).build();

            var ini = rdr.read(configIniFile);

            /* Interface (us) */
            var interfaceSection = ini.section("Interface");
            setAddress(interfaceSection.get("Address"));
            setDns(interfaceSection.getAllElse("DNS", new String[0]));

            String privateKey = interfaceSection.get("PrivateKey");
            if (privateKey != null && hasPrivateKey() && !privateKey.equals(PRIVATE_KEY_NOT_AVAILABLE)) {
                /*
                 * TODO private key should be removed from server at this point
                 */
                setUserPrivateKey(privateKey);
                setUserPublicKey(Keys.pubkeyBase64(privateKey).getBase64PublicKey());
            } else if (!hasPrivateKey()) {
                throw new IllegalStateException(
                        "Did not receive private key from server, and we didn't generate one on the client. Connection impossible.");
            }
            setPreUp(interfaceSection.contains("PreUp") ? String.join("\n", interfaceSection.getAll("PreUp")) : "");
            setPostUp(interfaceSection.contains("PostUp") ? String.join("\n", interfaceSection.getAll("PostUp")) : "");
            setPreDown(
                    interfaceSection.contains("PreDown") ? String.join("\n", interfaceSection.getAll("PreDown")) : "");
            setPostDown(interfaceSection.contains("PostDown") ? String.join("\n", interfaceSection.getAll("PostDown"))
                    : "");

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
            setPersistentKeepalive(peerSection.getInt("PersistentKeepalive"));
            setAllowedIps(peerSection.getAllElse("AllowedIPs", new String[0]));

            return "";
        } catch (IOException | ParseException ioe) {
            log.error("Failed to parse INI file.", ioe);
            return ioe.getMessage();
        }

    }

    @Override
    public void authorize() {
        ctx.getClientService().requestAuthorize(connection);
    }

    @Override
    public void authorized() {
        ctx.getClientService().authorized(connection);
    }

    @Override
    public void connect() {
        ctx.getClientService().connect(connection);
    }

    @Override
    public void deauthorize() {
        ctx.getClientService().deauthorize(connection);
    }

    @Override
    public void delete() {
        ctx.getClientService().delete(connection);
    }

    @Override
    public void disconnect(String reason) {
        ctx.getClientService().disconnect(connection, reason);
    }

    @Override
    public String getAddress() {

        return defaultIfBlank(connection.getAddress(), "");
    }

    @Override
    public String getMode() {
        return connection.getMode().name();
    }

    @Override
    public String[] getAllowedIps() {
        return connection.getAllowedIps().toArray(new String[0]);
    }

    @Override
    public String getDisplayName() {
        return defaultIfBlank(connection.getDisplayName(), "");
    }

    @Override
    public String getDefaultDisplayName() {
        return defaultIfBlank(connection.getDefaultDisplayName(), "");
    }

    @Override
    public String[] getDns() {
        return connection.getDns().toArray(new String[0]);
    }

    @Override
    public String getEndpointAddress() {
        return defaultIfBlank(connection.getEndpointAddress(), "");
    }

    @Override
    public int getEndpointPort() {
        return connection.getEndpointPort();
    }

    @Override
    public String getHostname() {
        return defaultIfBlank(connection.getHostname(), "");
    }

    @Override
    public long getId() {
        return connection.getId();
    }

    @Override
    public int getMtu() {
        return connection.getMtu();
    }

    @Override
    public String getName() {
        return defaultIfBlank(connection.getName(), "");
    }

    @Override
    public String getSerial() {
        return defaultIfBlank(connection.getSerial(), "");
    }

    @Override
    public String getPath() {
        return defaultIfBlank(connection.getPath(), "");
    }

    @Override
    public int getPersistentKeepalive() {
        return connection.getPersistentKeepalive();
    }

    @Override
    public int getPort() {
        return connection.getPort();
    }

    @Override
    public String getPublicKey() {
        return defaultIfBlank(connection.getPublicKey(), "");
    }

    @Override
    public String getStatus() {
        return ctx.getClientService().getStatusType(connection).name();
    }

    @Override
    public String getInterfaceName() {
        return ctx.getClientService().getStatus(getId()).getDetail().interfaceName();
    }

    @Override
    public String getUri(boolean withUsername) {
        return connection.getUri(withUsername);
    }

    @Override
    public String getBaseUri() {
        return connection.getBaseUri();
    }

    @Override
    public String getApiUri() {
        return connection.getApiUri();
    }

    @Override
    public String getClient() {
        return connection.getClient();
    }

    @Override
    public String[] getAuthMethods() {
        return Stream.of(connection.getAuthMethods()).map(AuthMethod::toString).toList().toArray(new String[0]);
    }

    @Override
    public String getConnectionTestUri(boolean withUsername) {
        return connection.getConnectionTestUri(withUsername);
    }

    @Override
    public String getUsernameHint() {
        return Utils.defaultIfBlank(connection.getUsernameHint(), "");
    }

    @Override
    public String getInstance() {
        return Utils.defaultIfBlank(connection.getInstance(), "");
    }

    @Override
    public String getUserPublicKey() {
        return defaultIfBlank(connection.getUserPublicKey(), "");
    }

    @Override
    public boolean hasPrivateKey() {
        return connection.getUserPrivateKey() != null && connection.getUserPrivateKey().length() > 0;
    }

    @Override
    public boolean isAuthorized() {
        return connection.isAuthorized();
    }

    @Override
    public boolean isConnectAtStartup() {
        return connection.isConnectAtStartup();
    }

    @Override
    public void setAsFavourite() {
        ctx.getClientService().setValue(getOwner(), ConfigurationItem.FAVOURITE, connection.getId());

    }

    @Override
    public boolean isFavourite() {
        return ctx.getClientService().getConnections(getOwner()).size() == 1 || ctx.getClientService()
                .getValue(getOwner(), ConfigurationItem.FAVOURITE).longValue() == connection.getId();
    }

    @Override
    public boolean isTransient() {
        return connection.isTransient();
    }

    @Override
    public long save() {
        return ctx.getClientService().save(connection).getId();
    }

    @Override
    public void setAddress(String address) {
        connection.setAddress(address);
    }

    @Override
    public void setAllowedIps(String[] allowedIps) {

        connection.setAllowedIps(Arrays.asList(allowedIps));
    }

    @Override
    public void setConnectAtStartup(boolean connectAtStartup) {
        connection.setConnectAtStartup(connectAtStartup);
    }

    @Override
    public void setDns(String[] dns) {
        connection.setDns(Arrays.asList(dns));
    }

    @Override
    public void setEndpointAddress(String endpointAddress) {
        connection.setEndpointAddress(endpointAddress);
    }

    @Override
    public void setEndpointPort(int endpointPort) {
        connection.setEndpointPort(endpointPort);
    }

    @Override
    public void setHostname(String host) {
        connection.setHostname(host);
        ConnectionUtil.setLastKnownServerIpAddress(connection);
    }

    @Override
    public void setName(String name) {
        connection.setName(Utils.isBlank(name) ? null : name);
    }

    @Override
    public void setSerial(String serial) {
        connection.setSerial(Utils.isBlank(serial) ? null : serial);
    }

    @Override
    public void setPersistentKeepalive(int peristentKeepalive) {
        connection.setPersistentKeepalive(peristentKeepalive);
    }

    @Override
    public void setPort(int port) {
        connection.setPort(port);
    }

    @Override
    public void setPublicKey(String publicKey) {
        connection.setPublicKey(publicKey);
    }

    @Override
    public void setUsernameHint(String usernameHint) {
        connection.setUsernameHint(usernameHint);
    }

    @Override
    public void setUserPrivateKey(String privateKey) {
        connection.setUserPrivateKey(privateKey);
    }

    @Override
    public void setUserPublicKey(String publicKey) {
        connection.setUserPublicKey(publicKey);
    }

    @Override
    public void update(String name, String uri, boolean connectAtStartup, boolean stayConnected) {
        connection.setName(name.equals("") ? null : name);
        connection.setConnectAtStartup(connectAtStartup);
        connection.setStayConnected(stayConnected);
        connection.updateFromUri(uri);
        if (!isTransient())
            save();
    }

    @Override
    public void setPath(String path) {
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
        return ctx.getClientService().getStatus(connection.getId()).getDetail().lastHandshake().toEpochMilli();
    }

    @Override
    public long getRx() {
        return ctx.getClientService().getStatus(connection.getId()).getDetail().rx();
    }

    @Override
    public long getTx() {
        return ctx.getClientService().getStatus(connection.getId()).getDetail().tx();
    }

    @Override
    public boolean isStayConnected() {
        return connection.isStayConnected();
    }

    @Override
    public void setStayConnected(boolean stayConnected) {
        connection.setStayConnected(stayConnected);
    }

    @Override
    public boolean isTemporarilyOffline() {
        return ctx.getClientService().getStatus(connection.getId())
                .getStatus() == ConnectionStatus.Type.TEMPORARILY_OFFLINE;
    }

    @Override
    public boolean isBlocked() {
        return ctx.getClientService().getStatus(connection.getId())
                .getStatus() == ConnectionStatus.Type.BLOCKED;
    }

    @Override
    public String getLastError() {
        var status = ctx.getClientService().getStatus(connection.getId());
        var detail = status.getDetail();
        if (detail != null && detail.error().isPresent())
            return detail.error().get();
        else
            return Utils.defaultIfBlank(status.getConnection().getError(), "");
    }

    @Override
    public String getAuthorizeUri() {
        return getUri(false) + ctx.getClientService().getStatus(connection.getId()).getAuthorizeUri();
    }

}
