package com.logonbox.vpn.client.attach.wireguard;

import com.gluonhq.attach.util.Services;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;

import java.util.Optional;

/**
 * Interface to be implemented by all platform specific classes that interface
 * with the underyling VPN network driver.
 */
public interface MobilePlatformService extends PlatformService<VirtualInetAddress<?>> {

    /**
     * Create a new instance of the service.
     * 
     * @return service
     */
    static Optional<MobilePlatformService> create() {
        return Services.get(MobilePlatformService.class);
    }
}
