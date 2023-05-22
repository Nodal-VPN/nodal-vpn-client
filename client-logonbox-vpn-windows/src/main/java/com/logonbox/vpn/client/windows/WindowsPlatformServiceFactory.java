package com.logonbox.vpn.client.windows;

import com.logonbox.vpn.client.PlatformServiceFactory;
import com.logonbox.vpn.client.wireguard.PlatformService;

import org.apache.commons.lang3.SystemUtils;

public class WindowsPlatformServiceFactory implements PlatformServiceFactory {

    @Override
    public boolean isSupported() {
        return SystemUtils.IS_OS_WINDOWS;
    }

    @Override
    public PlatformService<?> createPlatformService() {
        return new WindowsPlatformServiceImpl();
    }

}
