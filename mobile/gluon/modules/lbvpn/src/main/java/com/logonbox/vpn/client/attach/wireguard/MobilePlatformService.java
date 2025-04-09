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
package com.logonbox.vpn.client.attach.wireguard;

import com.gluonhq.attach.util.Services;
import com.logonbox.vpn.client.attach.wireguard.impl.DesktopPlatformService;
import com.logonbox.vpn.drivers.lib.PlatformService;
import com.logonbox.vpn.drivers.lib.PlatformServiceFactory;
import com.logonbox.vpn.drivers.lib.SystemContext;
import com.logonbox.vpn.drivers.lib.VpnAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Stream;

/**
 * Interface to be implemented by all platform specific classes that interface
 * with the underyling VPN network driver.
 */
public interface MobilePlatformService<ADDR extends VpnAddress> extends PlatformService<ADDR> {
    static Logger log = LoggerFactory.getLogger(MobilePlatformService.class);
    
    public static class MobilePlatformServiceFactory implements PlatformServiceFactory {

        @Override
        public PlatformService<? extends VpnAddress> createPlatformService(SystemContext ctx) {
            // TODO detect? not sure how this will interfact with gluons "target" (see grable)
            log.info("Loading delegate platform service.");
            if("android".equals(System.getProperty("javafx.platform"))) {
                throw new UnsupportedOperationException("TODO");
            }
            else if("ios".equals(System.getProperty("javafx.platform"))) {
                throw new UnsupportedOperationException("TODO");
            }
            else {
                Stream<Provider<PlatformServiceFactory>> impls;
                var layer = MobilePlatformService.class.getModule().getLayer();
                if(layer == null)
                    impls = ServiceLoader.load(PlatformServiceFactory.class).stream();
                else
                    impls = ServiceLoader.load(layer,  PlatformServiceFactory.class).stream();
                var del = impls.filter(
                        p -> !(p.get() instanceof MobilePlatformServiceFactory) && 
                        p.get().isSupported()).findFirst().orElseThrow(()-> new IllegalStateException("No supported desktop platform providers on modulepath")
                ).get().createPlatformService(ctx);
                log.info("Delegate platform service is {}.", del.getClass().getName());
                
                return new DesktopPlatformService(del);
            }
        }

        @Override
        public boolean isSupported() {
            return true;
        }
        
    }

    /**
     * Create a new instance of the service.
     * 
     * @return service
     */
    static Optional<MobilePlatformService<?>> create() {
        return Services.get(MobilePlatformService.class).map(mp -> (MobilePlatformService<?>)mp);
    }
}
