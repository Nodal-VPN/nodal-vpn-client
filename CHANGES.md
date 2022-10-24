# Changes

## 2.4.0-891

 * Only allow a single running instance of the GUI
 * Better reliability when there are adverse DNS conditions
 * Option to allow client to override MTU configuration.
 * Option to allow only a single connection to be active at a time. This on by defasult.
 * Make sure HTTP/2 is not used for status check calls.
 * Ignore certain network interfaces on Windows
 * Better detection of wireguard interface name on Windows
 * Fixed for Turkish Windows. 
 * Missing many translations.

## 2.4.0-812

 * Windows groups are now looked up by SID, not name, this should make it more reliable on
   non English installations..

## 2.4.0-692

 * Update server is now pinged from service, not the GUI
 * Icon and theme update
 * Fix for "Index 1 i null" error that appeared sometimes when there was more than one connection.

## 2.4.0-677

 * Dark branding colours for links in dark mode were hard to read
 * Better transition to remote authorisation page on first load.
 * Improved look of remote authorisation.
 * Better clean up on shutdown of service (e.g. removing dbus socket file)
 * Alternative configuration storage mechanism (to replace Derby in a later release)
 * Tool to convert to new storage mechanism.

## 2.4.0-609

 * DBus path on Mac could still be incorrect. 
 * Hack for bridge loss in UI when Mac coming out of hibernate.
 * Bumped JavaFX version to latest.
 * Fixes problems with service coming out of hibernate.

## 2.4.0-595

 * New simplified user interface. The "Burger" menu has been removed, and all connections
   are now visible on the new home page. "Back" navigation has been simplified.
 * The apply button has been removed from options. Any change to configuration is now
   applied immediately.
 * No timeout on logo caching for branding. If branded logo was not reachable, client
   could hang indefinitely at startup.
 * Re-work of configuration architecture to prevent possible crashes when preferences are
   moved, added or changed.
 * Authentication is now more important than updates (so an available update will not 
   interrupt authentication).
 * If there is already a JDK installed, the VPN client would sometimes use that instead
   of the embedded runtime. If the version of the runtime was insufficient, this could
   lead to crashes or other unexpected behaviour.
 * Possible fix for crashing when opening the window from the Mac OS taskbar "Open" action.
 * Added minimum and maximum startup window sizes.
 * If used with LogonBox VPN Server 2.3.12 or above, will now no longer de-authorize unless
   the configuration has actually been invalidate on the server. 
 * Default DNS method changed on Mac OS to "SCUtil (split)".
