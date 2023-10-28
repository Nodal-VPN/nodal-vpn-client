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
