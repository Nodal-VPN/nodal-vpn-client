package com.logonbox.vpn.client.mobile;

import com.logonbox.vpn.client.AbstractService;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVPN;

import java.util.List;
import java.util.UUID;

class EmbeddedVPN implements IVPN<EmbeddedVPNConnection> {
    /**
     * 
     */
    private final VpnManager<EmbeddedVPNConnection> vpnManager;
    private final LocalContext<EmbeddedVPNConnection> ctx;

    /**
     * @param main
     */
    EmbeddedVPN(LocalContext<EmbeddedVPNConnection> ctx, VpnManager<EmbeddedVPNConnection> vpnManager) {
        this.ctx = ctx;
        this.vpnManager = vpnManager;
    }


    @Override
    public String[] getKeys() {
        return ctx.getClientService().getKeys();
    }


    @Override
    public String getUUID() {
        UUID uuid = ctx.getClientService().getUUID(Main.getOwner());
        return uuid == null ? null : uuid.toString();
    }

    @Override
    public String getVersion() {
        return AppVersion.getVersion(AbstractService.ARTIFACT_COORDS);
    }

    @Override
    public long importConfiguration(String configuration) {
        return ctx.getClientService().importConfiguration(Main.getOwner(), configuration).getId();
    }

    @Override
    public EmbeddedVPNConnection getConnection(long id) {
        return new EmbeddedVPNConnection(ctx, ctx.getClientService().getStatus(id).getConnection());
    }


    @Override
    public List<EmbeddedVPNConnection> getConnections() {
        try {
            return ctx.getClientService().getStatus(Main.getOwner()).stream()
                    .map(s -> new EmbeddedVPNConnection(ctx, s.getConnection())).toList();
        } catch (Exception e) { 
            throw new IllegalStateException("Failed to get connections.", e);
        }
    }

    @Override
    public String getDeviceName() {
        return ctx.getClientService().getDeviceName();
    }

    @Override
    public long getConnectionIdForURI(String uri) {
        try {
            return ctx.getClientService().getStatus(Main.getOwner(), uri).getConnection().getId();
        } catch (IllegalArgumentException iae) {
            return -1;
        }
    }

    @Override
    public long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode) {
        try {
            ctx.getClientService().getStatus(Main.getOwner(), uri);
            throw new IllegalArgumentException(String.format("Connection with URI %s already exists.", uri));
        } catch (Exception e) {
            /* Doesn't exist */
            return ctx.getClientService().create(uri, Main.getOwner(), connectAtStartup, Mode.valueOf(mode), stayConnected).getId();
        }
    }

    @Override
    public long connect(String uri) {
        return ctx.getClientService().connect(Main.getOwner(), uri).getId();
    }

    @Override
    public int getNumberOfConnections() {
        return ctx.getClientService().getStatus(Main.getOwner()).size();
    }

    @Override
    public String getValue(String name) {
        ConfigurationItem<?> item = ConfigurationItem.get(name);
        Object val = ctx.getClientService().getValue(Main.getOwner(), item); 
        return val.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValue(String name, String value) {
        ctx.getClientService().setValue(Main.getOwner(), (ConfigurationItem<String>)ConfigurationItem.get(name), value);

    }

    @Override
    public int getIntValue(String key) {
        return (Integer)ctx.getClientService().getValue(Main.getOwner(), ConfigurationItem.get(key));
    }

    @Override
    public long getLongValue(String key) {
        return (Long)ctx.getClientService().getValue(Main.getOwner(), ConfigurationItem.get(key));
    }

    @Override
    public boolean getBooleanValue(String key) {
        return (Boolean)ctx.getClientService().getValue(Main.getOwner(), ConfigurationItem.get(key));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setIntValue(String key, int value) {
        ctx.getClientService().setValue(Main.getOwner(), (ConfigurationItem<Integer>)ConfigurationItem.get(key), value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setLongValue(String key, long value) {
        ctx.getClientService().setValue(Main.getOwner(), (ConfigurationItem<Long>)ConfigurationItem.get(key), value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setBooleanValue(String key, boolean value) {
        ctx.getClientService().setValue(Main.getOwner(), (ConfigurationItem<Boolean>)ConfigurationItem.get(key), value);     
    }

    @Override
    public void disconnectAll() {
        for (ConnectionStatus s : ctx.getClientService().getStatus(Main.getOwner())) {
            if (s.getStatus() != Type.DISCONNECTED && s.getStatus() != Type.DISCONNECTING) {
                try {
                    ctx.getClientService().disconnect(s.getConnection(), null);
                } catch (Exception re) {
                    Main.log.error("Failed to disconnect " + s.getConnection().getId(), re);
                }
            }
        }
    }

    @Override
    public int getActiveButNonPersistentConnections() {
        int active = 0;
        for (ConnectionStatus s : ctx.getClientService().getStatus(Main.getOwner())) {
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
        return vpnManager.getCertManager().isAccepted(encodedKey);
    }

    @Override
    public void acceptCert(String encodedKey) {
        vpnManager.getCertManager().accept(encodedKey);
    }

    @Override
    public void saveCert(String encodedKey) {
        vpnManager.getCertManager().save(encodedKey);
    }

    @Override
    public void rejectCert(String encodedKey) {
        vpnManager.getCertManager().reject(encodedKey);
    }

    @Override
    public boolean isMatchesAnyServerURI(String uri) {
        return ctx.getClientService().isMatchesAnyServerURI(Main.getOwner(), uri);
    }
}