package com.logonbox.vpn.client.windows;

import com.logonbox.vpn.client.common.PlatformUtilities;
import com.logonbox.vpn.client.common.Utils;
import com.sun.jna.platform.win32.Advapi32Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class WindowsPlatformUtilities implements PlatformUtilities {

    static Logger log = LoggerFactory.getLogger(WindowsPlatformUtilities.class);
    
    public final static String SID_ADMINISTRATORS_GROUP = "S-1-5-32-544";

    @Override
    public boolean isElevatableToAdministrator() {
        for (var grp : Advapi32Util.getCurrentUserGroups()) {
            if (SID_ADMINISTRATORS_GROUP.equals(grp.sidString)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getCurrentUser() {
        var username = System.getProperty("user.name");
        var domainOrComputer = System.getenv("USERDOMAIN");
        if (Utils.isBlank(domainOrComputer))
            return username;
        else
            return domainOrComputer + "\\" + username;
    }

    @Override
    public String getHostname() {
        return System.getenv("COMPUTERNAME");
    }

    @Override
    public String getApproxDarkModeColor() {
        return "#202020";
    }

    @Override
    public boolean openUrl(String url) {
        try {
            Runtime.getRuntime().exec(new String[] { "start", url });
            return true;
        } catch (IOException e) {
            log.error("Failed to open URL {}", url);
            return false;
        }
    }

}
