# Nodal VPN Client

A cross-platform VPN client, intended for use with the Nodal VPN Server. This builds
on our open source `nodal-core` library, and works best with [Nodal VPN Server](https://nodal.online/). 

# Architecture

The Nodal VPN suite consists of a number of interrelated modules, with some 
for concrete applications, and some shared across other components.

These may be arranged either as a monolithic application that directly manages VPN connections,
or a distributed application that is made up of several parts. The former is intended for use
on mobile platforms, while the latter more fully feature arrangement is intended for use on the
desktop. 

 * `nodal-vpn-client-desktop-service`, that runs as a privileged server and manages VPN configurations for
   all of the computers users.
 * `nodal-vpn-client-cli`, a command line application for creating and managing connections to VPN servers
 * `nodal-vpn-client-gui`, a desktop application with similar functionality to the CLI. This is required 
   if the web server uses web based authentication.
 * `nodal-vpn-client-tray`, a simple system tray application that provides quick access to VPN features. 

In both cases, `logonbox-vpn-drivers` will be used for low level VPN functionality.

### The VPN Manager Desktop Service

The `desktop-service` module manages the storage of all VPN configurations, and is
responsible for activating or de-activating those VPN configurations.

It requires privileged access to do so, so would normally be run as a local system
service will administrator privileges. 

### The CLI

A simple tool that is the user-interface to the Desktop Service. With it you may
create, edit, delete, start, stop, query and perform other operations on any VPN
connection.

The CLI lets you use Nodal VPN when you have no desktop, such as when remotely
connected to other systems or potentially on embedded systems.


### The GUI

A desktop application that provides similar functionality to the CLI,
but with an easy to use point and click user interface. 

It is also required to make use of some Nodal VPN server features, such as various
web based authentication providers. The GUI basically contains an embedded browser that
makes this possible. 

## Building

Standard Maven build targets may be used, with the additional profiles that may be 
activated.

`-P native-image` will natively compile all components. Must be called using a GraalVM
development kit, with access to all appropriate native tools. On Windows, must be called 
from withing a Visual Studio developer prompt (or compiler must be configured some other way).

`-P gluon-native-image` an exerimental option for building native applications using the 
Gluon framework. 


## Notes For Developers

 * All entry points, i.e. classes that contain a `main()` should be run with
   their respective module root as the main directory (i.e. the default in an
   Eclipse launcher).
 * When run in it's module root, the various distributed components will detect
   the `pom.xml` and consider it to be running in develope mode. In this case,
   dbus configuration may be altered.
 * All components may be run as normal users. If a component requires elevated 
   permissions, you will be prompted.