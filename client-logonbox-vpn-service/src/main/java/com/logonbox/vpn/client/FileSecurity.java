package com.logonbox.vpn.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;

import org.freedesktop.dbus.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.wireguard.windows.WindowsPlatformServiceImpl;
import com.sshtools.forker.common.OS;
import com.sun.jna.Platform;

/**
 * TODO: Some possible issues with this.
 * https://stackoverflow.com/questions/58700035/how-to-set-modify-acl-on-a-windows-file-using-java.
 * We are using English names for some groups / users on Windows here. This may
 * fail for other languages.
 *
 */
public class FileSecurity {

	public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(FileSecurity.class.getName());

	static Logger log = LoggerFactory.getLogger(FileSecurity.class);

	public static void restrictToUser(Path path) throws IOException {
		if (Platform.isLinux() || Util.isMacOs()) {
			var prms = Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
			log.info(String.format("Setting permissions on %s to %s", path, prms));
			Files.setPosixFilePermissions(path, new LinkedHashSet<>(
					prms));
		} else {
			var file = path.toFile();
			file.setReadable(false, false);
			file.setWritable(false, false);
			file.setExecutable(false, false);
			file.setReadable(true, true);
			file.setWritable(true, true);
			if (Platform.isWindows()) {
				List<AclEntry> acl = new ArrayList<>();
				if (OS.isAdministrator()) {
					try {
						acl.add(set(true, path, "Administrators", WindowsPlatformServiceImpl.SID_ADMINISTRATORS_GROUP, AclEntryType.ALLOW, AclEntryPermission.values()));
					} catch (Throwable upnfe2) {
						log.debug("Failed to add administrators permission.", upnfe2);
					}
					try {
						acl.add(set(true, path, "SYSTEM", WindowsPlatformServiceImpl.SID_SYSTEM, AclEntryType.ALLOW, AclEntryPermission.values()));
					} catch (Throwable upnfe2) {
						log.debug("Failed to add administrators permission.", upnfe2);
					}
				}
				if (acl.isEmpty()) {
					log.warn(String.format("Only basic permissions set for %s", path));
				} else {
					log.info(String.format("Setting permissions on %s to %s", path, acl));
					AclFileAttributeView aclAttr = Files.getFileAttributeView(path, AclFileAttributeView.class);
					aclAttr.setAcl(acl);
				}
			}
		}
	}

	public static void openToEveryone(Path path) throws IOException {
		if (Platform.isLinux() || Util.isMacOs()) {
			log.info(String.format("Setting permissions on %s to %s", path,
					Arrays.asList(PosixFilePermission.values())));
			Files.setPosixFilePermissions(path, new LinkedHashSet<>(Arrays.asList(PosixFilePermission.values())));
		} else if (Platform.isWindows()) {
			AclFileAttributeView aclAttr = Files.getFileAttributeView(path, AclFileAttributeView.class);
			List<AclEntry> acl = new ArrayList<>();
			if (OS.isAdministrator()) {
				try {
					acl.add(set(true, path, "Administrators", WindowsPlatformServiceImpl.SID_ADMINISTRATORS_GROUP, AclEntryType.ALLOW, AclEntryPermission.values()));
				} catch (Throwable upnfe2) {
					log.debug("Failed to add administrators permission.", upnfe2);
				}
				try {
					acl.add(set(true, path, "SYSTEM", WindowsPlatformServiceImpl.SID_SYSTEM, AclEntryType.ALLOW, AclEntryPermission.values()));
				} catch (Throwable upnfe2) {
					log.debug("Failed to add administrators permission.", upnfe2);
				}
			}
			try {
				acl.add(set(true, path, "Everyone", WindowsPlatformServiceImpl.SID_WORLD, AclEntryType.ALLOW, AclEntryPermission.READ_DATA,
						AclEntryPermission.WRITE_DATA));
			} catch (Throwable upnfe) {
				System.err.println("Everyone failed");
				upnfe.printStackTrace();
			}
			try {
				acl.add(set(true, path, "Users", WindowsPlatformServiceImpl.SID_USERS, AclEntryType.ALLOW, AclEntryPermission.READ_DATA,
						AclEntryPermission.WRITE_DATA));
			} catch (Throwable upnfe2) {
				System.err.println("Users failed");
				upnfe2.printStackTrace();
			}
			if (acl.isEmpty()) {

				/* No everyone user, fallback to basic java.io.File methods */
				log.warn("Falling back to basic file permissions method.");
				path.toFile().setReadable(true, false);
				path.toFile().setWritable(true, false);
			} else {
				aclAttr.setAcl(acl);
			}
		} else
			log.warn(String.format(
					"Cannot open permissions for %s on this platform, clients may not be able to connect.", path));
	}

	protected static AclEntry set(boolean asGroup, Path path, String name, String sid,  AclEntryType type,
			AclEntryPermission... perms) throws IOException {
		try {
			System.err.println("Trying to set '" + sid + "' or name of " + name + " on "  +path + " as Group: " + asGroup + " : " + type + " : " + Arrays.asList(perms));
			String bestRealName = WindowsPlatformServiceImpl.getBestRealName(sid, name);
			System.err.println("Best real name : "+ bestRealName);
			return perms(asGroup, path, bestRealName, type, perms);
		}
		catch(Throwable t) {
			t.printStackTrace();
			System.err.println("Failed to get AclEntry using either SID of '" + sid + "' or name of " + name + ". Attempting using localised name.");
			return perms(asGroup, path, name, type, perms);
		}
	}

	protected static AclEntry perms(boolean asGroup, Path path, String name, AclEntryType type,
			AclEntryPermission... perms) throws IOException {
		UserPrincipalLookupService upls = path.getFileSystem().getUserPrincipalLookupService();
		UserPrincipal user = asGroup ? upls.lookupPrincipalByGroupName(name) : upls.lookupPrincipalByName(name);
		AclEntry.Builder builder = AclEntry.newBuilder();
		builder.setPermissions(EnumSet.copyOf(Arrays.asList(perms)));
		builder.setPrincipal(user);
		builder.setType(type);
		return builder.build();
	}
}
