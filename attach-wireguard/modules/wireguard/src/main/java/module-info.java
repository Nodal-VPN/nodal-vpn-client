import com.logonbox.vpn.client.PlatformServiceFactory;

/**
 * LogonBox VPN Client Gluon Mobile Attach Library
 */
module com.logonbox.vpn.client.attach.wireguard {

    requires com.gluonhq.attach.util;
    requires transitive com.logonbox.vpn.client;

    exports com.logonbox.vpn.client.attach.wireguard;
    exports com.logonbox.vpn.client.attach.wireguard.impl to com.gluonhq.attach.util;

    uses PlatformServiceFactory;
}