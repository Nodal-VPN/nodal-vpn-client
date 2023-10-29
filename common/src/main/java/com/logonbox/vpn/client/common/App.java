package com.logonbox.vpn.client.common;

import java.nio.file.Path;
import java.nio.file.Paths;

public class App {

    public final static Path CLIENT_HOME = Paths.get(System.getProperty("user.home"), ".logonbox-vpn-client");
    public final static Path CLIENT_CONFIG_HOME = CLIENT_HOME.resolve("conf");
}
