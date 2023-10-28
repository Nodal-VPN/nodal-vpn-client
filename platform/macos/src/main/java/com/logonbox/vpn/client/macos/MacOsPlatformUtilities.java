package com.logonbox.vpn.client.macos;

import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

public class MacOsPlatformUtilities implements PlatformUtilities {
    static Logger log = LoggerFactory.getLogger(MacOsPlatformUtilities.class);

    @Override
    public boolean isElevatableToAdministrator() {
        try {
            return Arrays.asList(String.join(" ", Utils.runCommandAndCaptureOutput("id", "-Gn")).split("\\s+"))
                    .contains("admin");
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
        return "#231f25";
    }

    @Override
    public boolean openUrl(String url) {
        try {
            Runtime.getRuntime().exec(new String[] { "open", url });
            return true;
        } catch (IOException e) {
            log.error("Failed to open URL {}", url);
            return false;
        }
    }
}
