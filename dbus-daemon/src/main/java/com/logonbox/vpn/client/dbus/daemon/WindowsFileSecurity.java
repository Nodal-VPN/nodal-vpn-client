/**
 * Copyright © 2023 LogonBox Limited (support@logonbox.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.logonbox.vpn.client.dbus.daemon;

import com.sun.jna.platform.win32.Advapi32Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.ResourceBundle;

import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class WindowsFileSecurity {

    public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(WindowsFileSecurity.class.getName());

    public final static String SID_ADMINISTRATORS_GROUP = "S-1-5-32-544";
    public final static String SID_WORLD = "S-1-1-0";
    public final static String SID_USERS = "S-1-5-32-545";
    public final static String SID_SYSTEM = "S-1-5-18";
    
    static Logger LOG = LoggerFactory.getLogger(WindowsFileSecurity.class);

    static void restrictToUser(Path path) throws IOException {
        var file = path.toFile();
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
        var acl = new ArrayList<AclEntry>();
        if (SystemUtil.isAdministrator()) {
            try {
                acl.add(set(true, path, "Administrators", SID_ADMINISTRATORS_GROUP,
                        AclEntryType.ALLOW, AclEntryPermission.values()));
            } catch (Throwable upnfe2) {
                LOG.debug("Failed to add administrators permission.", upnfe2);
            }
            try {
                acl.add(set(true, path, "SYSTEM", SID_SYSTEM, AclEntryType.ALLOW,
                        AclEntryPermission.values()));
            } catch (Throwable upnfe2) {
                LOG.debug("Failed to add administrators permission.", upnfe2);
            }
        }
        if (acl.isEmpty()) {
            LOG.warn("Only basic permissions set for {}", path);
        } else {
            LOG.info("Setting permissions on {} to {}", path, acl);
            var aclAttr = Files.getFileAttributeView(path, AclFileAttributeView.class);
            aclAttr.setAcl(acl);
        }
    }

    static void openToEveryone(Path path) throws IOException {
        var aclAttr = Files.getFileAttributeView(path, AclFileAttributeView.class);
        var acl = new ArrayList<AclEntry>();
        if (SystemUtil.isAdministrator()) {
            try {
                acl.add(set(true, path, "Administrators", SID_ADMINISTRATORS_GROUP,
                        AclEntryType.ALLOW, AclEntryPermission.values()));
            } catch (Throwable upnfe2) {
                LOG.debug("Failed to add administrators permission.", upnfe2);
            }
            try {
                acl.add(set(true, path, "SYSTEM", SID_SYSTEM, AclEntryType.ALLOW,
                        AclEntryPermission.values()));
            } catch (Throwable upnfe2) {
                LOG.debug("Failed to add administrators permission.", upnfe2);
            }
        }
        try {
            acl.add(set(true, path, "Everyone", SID_WORLD, AclEntryType.ALLOW,
                    AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA));
        } catch (Throwable upnfe) {
            LOG.warn("Failed to set Everyone permission.", upnfe);
        }
        try {
            acl.add(set(true, path, "Users", SID_USERS, AclEntryType.ALLOW,
                    AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA));
        } catch (Throwable upnfe2) {
            LOG.warn("Failed to set Users permission.", upnfe2);
        }
        if (acl.isEmpty()) {

            /* No everyone user, fallback to basic java.io.File methods */
            LOG.warn("Falling back to basic file permissions method.");
            path.toFile().setReadable(true, false);
            path.toFile().setWritable(true, false);
        } else {
            aclAttr.setAcl(acl);
        }
    }

    private static AclEntry set(boolean asGroup, Path path, String name, String sid, AclEntryType type,
            AclEntryPermission... perms) throws IOException {
        try {
            LOG.debug("Trying to set {} or name of {} on {} as group {} : {} : {}", sid, name, path, asGroup, type,
                    Arrays.asList(perms));
            var bestRealName = getBestRealName(sid, name);
            LOG.debug("Best real name : " + bestRealName);
            return perms(asGroup, path, bestRealName, type, perms);
        } catch (Throwable t) {
            LOG.debug("Failed to get AclEntry using either SID of {} or name of {}. Attempting using localised name",
                    sid, name, t);
            return perms(asGroup, path, name, type, perms);
        }
    }

    private static AclEntry perms(boolean asGroup, Path path, String name, AclEntryType type,
            AclEntryPermission... perms) throws IOException {
        var upls = path.getFileSystem().getUserPrincipalLookupService();
        var user = asGroup ? upls.lookupPrincipalByGroupName(name) : upls.lookupPrincipalByName(name);
        var builder = AclEntry.newBuilder();
        builder.setPermissions(EnumSet.copyOf(Arrays.asList(perms)));
        builder.setPrincipal(user);
        builder.setType(type);
        return builder.build();
    }

    private static String getBestRealName(String sid, String name) {
        try {
            if (sid == null)
                throw new NullPointerException();
            var acc = Advapi32Util.getAccountBySid(sid);
            return acc.name;
        } catch (Exception e) {
            /* Fallback to i18n */
            LOG.warn("Falling back to I18N strings to determine best real group name for {}", name);
            return WindowsFileSecurity.BUNDLE.getString(name);
        }
    }
}
