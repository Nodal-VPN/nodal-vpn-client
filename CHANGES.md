# Changes

## 2.4.0-????

 * Dark branding colours for links in dark mode were hard to read
 * Better transition to remote authorisation page on first load.
 * Improved look of remote authorisation.

## 2.4.0-690

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
