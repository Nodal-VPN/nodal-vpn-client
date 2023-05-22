package com.logonbox.vpn.client.linux;

import com.logonbox.vpn.client.PlatformServiceFactory;
import com.logonbox.vpn.client.wireguard.PlatformService;

import org.apache.commons.lang3.SystemUtils;

public class LinuxPlatformServiceFactory implements PlatformServiceFactory {

    @Override
    public boolean isSupported() {
        return SystemUtils.IS_OS_LINUX;
    }

    @Override
    public PlatformService<?> createPlatformService() {
        return new LinuxPlatformServiceImpl();
    }

}
