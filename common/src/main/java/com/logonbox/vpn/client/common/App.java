package com.logonbox.vpn.client.common;

import java.io.File;

public class App {

    public final static File CLIENT_HOME = new File(
            System.getProperty("user.home") + File.separator + ".logonbox-vpn-client");
    public final static File CLIENT_CONFIG_HOME = new File(CLIENT_HOME, "conf");
}
