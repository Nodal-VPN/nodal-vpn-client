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
