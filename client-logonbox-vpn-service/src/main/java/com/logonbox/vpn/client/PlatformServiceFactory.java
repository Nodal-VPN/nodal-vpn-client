package com.logonbox.vpn.client;

import com.logonbox.vpn.client.wireguard.PlatformService;

public interface PlatformServiceFactory {
    
    boolean isSupported();

	PlatformService<?> createPlatformService();
}
