package com.logonbox.vpn.client.common.dbus;

import com.sshtools.jadbus.lib.JadbusAddress;
import com.sshtools.liftlib.OS;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

public final class BusFactory {

    private static Logger log = LoggerFactory.getLogger(BusFactory.class);

    public final static class Builder {
        private Optional<Path> properties = Optional.empty();
        private boolean developer = Files.exists(Paths.get("pom.xml"));
        private Optional<Boolean> jadbus = Optional.empty();
        private Optional<Boolean> systemBus = Optional.empty();
        private Optional<String> address = Optional.empty();
        private Optional<Consumer<DBusConnectionBuilder>> builderConfigurator = Optional.empty();
        
        public Builder withDeveloper(boolean developer) {
            this.developer = developer;
            return this;
        }
        
        public Builder withProperties(Path properties) {
            this.properties = Optional.of(properties);
            return this;
        }

        public Builder withSystemBus() {
            this.systemBus = Optional.of(true);
            return this;
        }

        public Builder withSessionBus() {
            this.systemBus = Optional.of(false);
            return this;
        }

        public Builder withJadbus(boolean jadbus) {
            this.jadbus = Optional.of(jadbus);
            return this;
        }

        public Builder withAddress(String address) {
            this.address = Optional.ofNullable(address);
            return this;
        }

        public Builder withConnectionBuilderConfigurator(Consumer<DBusConnectionBuilder> configuration) {
            this.builderConfigurator = Optional.of(configuration);
            return this;
        }

        public BusFactory build() {
            return new BusFactory(this);
        }
    }

    private final Optional<Boolean> jadbus;
    private final String address;
    private final Optional<Consumer<DBusConnectionBuilder>> builderConfigurator;
    private final Optional<Boolean> systemBus;
    private final Optional<Path> properties;
    private final boolean developer;

    private BusFactory(Builder builder) {
        this.properties = builder.properties;        
        this.developer = builder.developer;        
        this.address = builder.address.orElse(null);
        this.builderConfigurator = builder.builderConfigurator;
        this.jadbus = builder.jadbus;
        this.systemBus = builder.systemBus;
    }

    public DBusConnection connection() throws IOException, DBusException {
        DBusConnection conn;
        if (isSideBySideDbusDaemonAvailable()) {
            String addr;
            try (var in = Files.newBufferedReader(getSideBySideDbusDaemonPropertiesPath())) {
                var p = new Properties();
                p.load(in);
                addr = p.getProperty("address");
            }
            log.info(String.format("Connecting to DBus @%s", addr));
            conn = configureBuilder(DBusConnectionBuilder.forAddress(addr)).build();
        } else {
            log.info("Looking for DBus");
            if (address != null) {
                log.info(String.format("Connecting to specific DBus @%s", address));
                conn = configureBuilder(DBusConnectionBuilder.forAddress(address)).build();
            } else {
                var useJadbus = jadbus.orElseGet(() -> !OS.isLinux());
                var useSystemBus = systemBus.orElseGet(() -> OS.isAdministrator());
                if (useJadbus) {
                    if (useSystemBus) {
                        log.info("Connecting to Jadbus System DBus");
                        conn = configureBuilder(DBusConnectionBuilder.forAddress(JadbusAddress.systemBus())).build();
                    } else {
                        log.info("Per configuration, connecting to Jadbus Session DBus");
                        conn = configureBuilder(DBusConnectionBuilder.forAddress(JadbusAddress.sessionBus(false)))
                                .build();
                    }
                } else {
                    if (useSystemBus) {
                        log.info("Connecting to System DBus");
                        conn = configureBuilder(DBusConnectionBuilder.forSystemBus()).build();
                    } else {
                        log.info("Per configuration, connecting to Session DBus");
                        conn = configureBuilder(DBusConnectionBuilder.forSessionBus()).build();
                    }
                }
            }
        }
        log.info("Connected to {}", conn.getAddress());
        return conn;
    }

    private DBusConnectionBuilder configureBuilder(DBusConnectionBuilder bldr) {
        builderConfigurator.ifPresent(bc -> bc.accept(bldr));
        return bldr;
    }

    private boolean isSideBySideDbusDaemonAvailable() {
        if (developer) {
            log.info("In developer mode (pom.xml exists), looking for side-by-side `dbus-daemon` running instance.");
            var dbusProps = getSideBySideDbusDaemonPropertiesPath();
            if (Files.exists(dbusProps)) {
                log.warn(
                        "Using developer dbus-daemon that exists side-by-side with this module. If this is not what you want, remove {}",
                        dbusProps);
                return true;
            } else
                log.info("No side-by-side daemon, using default algorithm to determine bus address");
        } else if (Files.exists(getSideBySideDbusDaemonPropertiesPath())) {
            return true;
        }
        return false;

    }

    private Path getSideBySideDbusDaemonPropertiesPath() {
        return properties.orElseGet(() -> {
            if (developer) {
                return  Paths.get("..", "dbus-daemon", "conf", "dbusd.properties");
            } else {
                return Paths.get("conf", "dbusd.properties");
            }    
        });
        
    }
}
