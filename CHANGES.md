# Changes

## 2.4.1

 * No timeout on logo caching for branding. If branded logo was not reacbable, client
   could hang indefinitely at startup.
 * Re-work of configuration architecture to prevent possible crashes when preferences are
   moved, added or changed.
 * Authentication is now more important than updates (so an available update will not 
   interupt authentication).
