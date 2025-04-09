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
package com.logonbox.vpn.client.common;

import java.util.ServiceLoader;

public interface PlatformUtilities {
    
    public final static class Default {
        private static PlatformUtilities DEFAULT = create();
        
        private static PlatformUtilities create() {
            return ServiceLoader.load(PlatformUtilities.class).findFirst().orElseThrow(() -> new IllegalStateException("No platforum utility implementations available. Check your classpath / modulepath."));
        }
    }
    
    public static PlatformUtilities get() {
        return Default.DEFAULT;
    }
    
    boolean isElevatableToAdministrator();
    
    default String getCurrentUser() {
        return System.getProperty("user.name");
    }
    
    String getHostname();
    
    String getApproxDarkModeColor();
    
    boolean openUrl(String url);
}
