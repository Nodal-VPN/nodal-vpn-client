package com.logonbox.vpn.client.dbus.daemon;

import org.freedesktop.dbus.utils.Util;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class SystemUtil {

    static boolean isAdministrator() {
        if (Util.isWindows()) {
            try {
                String programFiles = System.getenv("ProgramFiles");
                if (programFiles == null) {
                    programFiles = "C:\\Program Files";
                }
                File temp = new File(programFiles, UUID.randomUUID().toString() + ".txt");
                temp.deleteOnExit();
                if (temp.createNewFile()) {
                    temp.delete();
                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
        if (isUnixLike()) {
            return getAdministratorUsername().equals(System.getProperty("user.name"));
        }
        throw new UnsupportedOperationException();
    }

    static String getAdministratorUsername() {
        if (Util.isWindows()) {
            return System.getProperty("liftlib.administratorUsername",
                    System.getProperty("liftlib.rootUser", "Administrator"));
        }
        if (isUnixLike()) {
            return System.getProperty("liftlib.administratorUsername", System.getProperty("liftlib.rootUser", "root"));
        }
        throw new UnsupportedOperationException();
    }

    static boolean isUnixLike() {
        return !Util.isWindows();
    }
}
