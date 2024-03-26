# Changes

## 3.0.5-SNAPSHOT

 * In progress

## 3.0.4-378

 * Resizing the Window on Linux can corrupt the window borders.
 * Adds support for the next generation LogonBox VPN server. 
 * Fix for occasional UI corruption (e.g. in Options page) where all component text
   was lost.
 * Under some circumstances it may be possible start a 2nd instance of the service, which
   would result in unexpected behaviour. Additional checks to prevent this have been added.
 * Excessive key validity checks were being made (during authorization).
 * Linux clients will connect again if Route All is enabled
 * Removed old uses of route command on Linux client, should be using ip route everywhere now

## 3.0.3-256

 * Windows installer now signed with EV certificate (by our sister company JADAPTIVE).
 * Fixed an issue with the updater mechanism. Updates from 3.0.2 to this version need to be done manually but should be ok from this point onwards.

## 3.0.2-223

 * Stability fixes. 
 * Wrong update phase could sometimes be picked for updater.
 * On Windows, client would enter infinite loop if there were more than 4 stale DBUS
   socket files and look like it has not started. 

## 3.0.1-204

 * Work-around and diagnostics for memory allocation error on Windows.
 * Hook scripts on Windows broken

## 3.0.0-120

 * New major release.
 * Connections validity is now preemptively checked, resulting in faster
   connection times and faster re-authentication in most circumstances. 
 * Fixed some issues with basic wireguard INI files.
 * Windows now uses WireGuardNT Kernel driver. This should result in noticeably
   better performance and reliability.
 * Windows network configuration service (supports a single wireguard service), 
   is now natively compiled, resulting in much faster startup time and vastly 
   reduced memory and CPU usage. This is only available on x64. 32bit Windows
   will fallback to interpreted mode.
 * Updated to latest wireguard-go and wg commands for Mac OS.
 * Update countdown doesn't stop if automatic updates are enabled.
 * Notification popups could show a an empty window on the desktop on Windows
   and Mac OS.
 * Updated to JavaFX 20.0.1, JDK 20.
 * Activated alternative connection storage mechanism (.ini type files). All
   existing configurations should now be converted.
 * LastKnownServerIpAddress not being reset when hostname changes.
 * Always regenerate private key when the connection is de-authorized (e.g. expired)
 * Starting a connection from the Windows systray will bring the client to front if a new authentication is prompted for.

## 2.4.0-1150

 * Improvements to CPU usage on Windows, particularly when coming out of sleep. 

## 2.4.0-1014

 * Mac OS X networksetup DNS integration not correctly removing DNS server address and domains on tear down.
 * DBus permissions on French installs would sometimes be incorrect result in failure of front-end to connect to service.

## 2.4.0-992

 * May prevent updates by non-administrative users during installing.
 * CHANGES.md not actually included in any installer components.
 * Another case where existing network interfaces might not be matched, 
   leaving stale network interfaces running. 

## 2.4.0-938

 * Device identity cookie (LBVPNDID) not sent during connection test if IP address fallback is being used.
 * Problems with timers when computer comes out of hibernate/sleep. This could cause the client to get
   stuck in states such as "Temporarily Offline".
 * Multiple device identifier cookies could be saved, causing unexpected reauthorizations.
 * VPN server has improper handling of v1 cookies, as used by the VPN client. This has been fixed on
   the server. 

## 2.4.0-921
 * CHANGES.md included in binary distribution
 * Mac/Windows launch at end of install wasn't working correctly.

## 2.4.0-919

 * Automatic updates not triggering.
 * Only allow a single running instance of the GUI
 * Better reliability when there are adverse DNS conditions
 * Option to allow client to override MTU configuration.
 * Option to allow only a single connection to be active at a time. This on by default.
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
