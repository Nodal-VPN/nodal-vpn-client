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
package com.logonbox.vpn.client.linux;

import static com.logonbox.vpn.client.common.Utils.runCommandAndCaptureOutput;

import com.logonbox.vpn.client.common.PlatformUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;


public class LinuxPlatformUtilities implements PlatformUtilities {
    static Logger log = LoggerFactory.getLogger(LinuxPlatformUtilities.class);

    @Override
    public boolean isElevatableToAdministrator() {
        try {
            return Arrays.asList(String.join(" ", runCommandAndCaptureOutput("id", "-Gn")).split("\\s+")).contains("sudo");
        } catch (IOException e) {
           log.error("Failed to test for user groups, assuming can elevate.", e);
           return true;
        }
    }

    @Override
    public String getHostname() {
        return System.getenv("HOSTNAME");
    }

    @Override
    public String getApproxDarkModeColor() {
        return "#1c1f22";
    }

    @Override
    public boolean openUrl(String url) {
        try {
            Runtime.getRuntime().exec(new String[] { "xdg-open", url });
            return true;
        } catch (IOException e) {
            log.error("Failed to open URL {}", url);
            return false;
        }
    }

}
