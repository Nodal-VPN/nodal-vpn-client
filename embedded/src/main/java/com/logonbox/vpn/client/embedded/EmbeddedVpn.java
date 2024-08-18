package com.logonbox.vpn.client.embedded;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.common.AppVersion;
import com.logonbox.vpn.client.common.ConfigurationItem;
import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.api.IVpn;
import com.logonbox.vpn.drivers.lib.DNSProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.ServiceLoader;
import java.util.UUID;

public class EmbeddedVpn implements IVpn<EmbeddedVpnConnection> {

    static Logger log = LoggerFactory.getLogger(EmbeddedVpn.class);
    
    private final LocalContext<EmbeddedVpnConnection> ctx;

    /**
     * @param main
     */
    public EmbeddedVpn(LocalContext<EmbeddedVpnConnection> ctx) {
        this.ctx = ctx;
    }

    @Override
    public String[] getKeys() {
        return ctx.getClientService().getKeys();
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
    public String getUUID() {
        UUID uuid = ctx.getClientService().getUUID(getOwner());
        return uuid == null ? null : uuid.toString();
    }

    @Override
    public String getVersion() {
        return AppVersion.getVersion("com.logonbox", "client-logonbox-vpn-mobile");
    }

    @Override
    public long importConfiguration(String configuration) {
        return ctx.getClientService().importConfiguration(getOwner(), configuration).getId();
    }

    @Override
    public EmbeddedVpnConnection getConnection(long id) {
        return new EmbeddedVpnConnection(ctx, ctx.getClientService().getStatus(id).getConnection());
    }

    @Override
    public EmbeddedVpnConnection[] getConnections() {
        try {
            return ctx.getClientService().getStatus(getOwner()).stream()
                    .map(s -> new EmbeddedVpnConnection(ctx, s.getConnection())).toList().toArray(new EmbeddedVpnConnection[0]);
        } catch (Exception e) { 
            throw new IllegalStateException("Failed to get connections.", e);
        }
    }

    @Override
    public EmbeddedVpnConnection[] getAllConnections() {
        try {
            return ctx.getClientService().getStatus("").stream()
                    .map(s -> new EmbeddedVpnConnection(ctx, s.getConnection())).toList().toArray(new EmbeddedVpnConnection[0]);
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
            return ctx.getClientService().getStatus(getOwner(), uri).getConnection().getId();
        } catch (IllegalArgumentException iae) {
            return -1;
        }
    }

    @Override
    public long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode) {
        try {
            ctx.getClientService().getStatus(getOwner(), uri);
            throw new IllegalArgumentException(String.format("Connection with URI %s already exists.", uri));
        } catch (Exception e) {
            /* Doesn't exist */
            return ctx.getClientService().create(uri, getOwner(), connectAtStartup, Mode.valueOf(mode), stayConnected).getId();
        }
    }

    @Override
    public long connect(String uri) {
        return ctx.getClientService().connect(getOwner(), uri).getId();
    }

    @Override
    public int getNumberOfConnections() {
        return ctx.getClientService().getStatus(getOwner()).size();
    }

    @Override
    public String getValue(String name) {
        ConfigurationItem<?> item = ConfigurationItem.get(name);
        Object val = ctx.getClientService().getValue(getOwner(), item); 
        return val.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValue(String name, String value) {
        ctx.getClientService().setValue(getOwner(), (ConfigurationItem<String>)ConfigurationItem.get(name), value);

    }

    @Override
    public int getIntValue(String key) {
        return (Integer)ctx.getClientService().getValue(getOwner(), ConfigurationItem.get(key));
    }

    @Override
    public long getLongValue(String key) {
        return (Long)ctx.getClientService().getValue(getOwner(), ConfigurationItem.get(key));
    }

    @Override
    public boolean getBooleanValue(String key) {
        return (Boolean)ctx.getClientService().getValue(getOwner(), ConfigurationItem.get(key));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setIntValue(String key, int value) {
        ctx.getClientService().setValue(getOwner(), (ConfigurationItem<Integer>)ConfigurationItem.get(key), value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setLongValue(String key, long value) {
        ctx.getClientService().setValue(getOwner(), (ConfigurationItem<Long>)ConfigurationItem.get(key), value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setBooleanValue(String key, boolean value) {
        ctx.getClientService().setValue(getOwner(), (ConfigurationItem<Boolean>)ConfigurationItem.get(key), value);     
    }

    @Override
    public void disconnectAll() {
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
        return ctx.getClientService().isMatchesAnyServerURI(getOwner(), uri);
    }
    
    public static String getOwner() {
        return System.getProperty("user.name");
    }
}