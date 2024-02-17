package com.logonbox.vpn.client.common;

import java.text.MessageFormat;
import java.util.Arrays;

public enum AuthMethod {
    DEVICE_TOKEN, EMBEDDED_BROWSER, HTTP_BASIC;
    
    public static AuthMethod[] supported(boolean gui) {
        if(gui) {
            return new AuthMethod[] { EMBEDDED_BROWSER, DEVICE_TOKEN };
        }
        else {
            return new AuthMethod[] { DEVICE_TOKEN, HTTP_BASIC };
        }
    }

    public static AuthMethod select(boolean gui, String[] authMethods) {
        var supportedNames = Arrays.asList(supported(gui)).stream().map(AuthMethod::name).toList();
        for(var s : authMethods) {
            if(supportedNames.contains(s)) {
                return AuthMethod.valueOf(s);
            }
        }
        throw new IllegalArgumentException(MessageFormat.format(
                "None of the supplied authentication methods ({0}) are supported ({1}).", String.join(",", Arrays.asList(authMethods)), String.join(",", supportedNames)));
    }
}
