package com.logonbox.vpn.client.macos;

import com.logonbox.vpn.client.PlatformServiceFactory;
import com.logonbox.vpn.client.wireguard.PlatformService;

import org.apache.commons.lang3.SystemUtils;

public class MacOsPlatformServiceFactory implements PlatformServiceFactory {

    @Override
    public boolean isSupported() {
        return SystemUtils.IS_OS_MAC_OSX;
    }

    @Override
    public PlatformService<?> createPlatformService() {
        return new BrewOSXPlatformServiceImpl();
    }

}
