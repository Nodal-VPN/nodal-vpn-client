
/**
 * VPN Client Gluon Mobile Attach Library
 */

import com.logonbox.vpn.client.attach.wireguard.MobilePlatformService.MobilePlatformServiceFactory;
import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;

module com.logonbox.vpn.client.attach.lbvpn {

    requires com.gluonhq.attach.util;
    requires transitive com.jadaptive.nodal.core.lib;
    //requires static com.logonbox.vpn.drivers.os;

    exports com.logonbox.vpn.client.attach.wireguard;
    exports com.logonbox.vpn.client.attach.wireguard.impl/*
                                                          * to com.gluonhq.attach.util, com.logonbox.vpn.client.mobile
                                                          */;

    provides PlatformServiceFactory with MobilePlatformServiceFactory;
    uses PlatformServiceFactory;
}