# Changes

## 2.4.1

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
