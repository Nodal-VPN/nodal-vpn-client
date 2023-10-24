package com.logonbox.vpn.client.dbus.daemon;

import com.logonbox.vpn.client.logging.SimpleLoggerConfiguration;

import org.freedesktop.dbus.bin.EmbeddedDBusDaemon;
import org.freedesktop.dbus.connections.BusAddress;
import org.freedesktop.dbus.connections.transports.TransportBuilder;
import org.freedesktop.dbus.connections.transports.TransportBuilder.SaslAuthMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "lbvpn-dbus-daemon", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "A DBus Daemon.")
public class DBusD implements Callable<Integer> {
    static Logger log;

    public static void main(String[] args) throws Exception {
        System.exit(new CommandLine(new DBusD()).execute(args));
    }

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    private Optional<Level> level;

    @Option(names = { "-C", "--log-to-console" }, description = "Direct all log output to console.")
    private boolean logToConsole;

    @Option(names = { "-l", "--listen" }, paramLabel = "SPEC", description = "DBus address to listen on.")
    private Optional<String> address;

    @Option(names = { "-I",
            "--insecure" }, description = "Apply loose permissions to any generate file, such as `--addressfile`.")
    private boolean insecure;

    @Option(names = { "-p", "--pidfile",
            "--pid-file" }, paramLabel = "FILE", description = "Path of file to save this processes PID to.")
    private Optional<Path> pidfile;

    @Option(names = { "-a", "--addressfile",
            "--address-file" }, paramLabel = "FILE", description = "Path of file to save this processes PID to.")
    private Optional<Path> addressfile;

    @Option(names = { "-K",
            "--address-file-key" }, paramLabel = "KEY", description = "Makes `--addressfile` be stored in properties format, with this value as the key.")
    private Optional<String> addressfileKey;

    @Option(names = { "-A", "--addresspref",
            "--address-pref" }, paramLabel = "SPEC", description = "Store the address in preference. This may be `default`, or <node>:<key>.")
    private Optional<String> addresspref;

    @Option(names = { "-U",
            "--address-userpref" }, description = "Store the address in preference. This may be `default`, or <node>:<key>.")
    private boolean addressuserpref;

    @Option(names = { "-r",
            "--print-address" }, description = "Apply loose permissions to any generate file, such as `--addressfile`.")
    private boolean printaddress;

    @Option(names = { "-u",
            "--unix" }, description = "Create a UNIX domain socket to listen for clients. Note, incompatible with --tcp")
    private boolean unix;

    @Option(names = { "-t",
            "--tcp" }, description = "Create a TCP socket to listen for clients. Note, incompatible with --unix")
    private boolean tcp;

    @Option(names = { "-m", "--auth-mode" }, paramLabel = "MODE", description = "SASL authentication mode.")
    private SaslAuthMode authMode;

    @Override
    public Integer call() throws Exception {
        if (logToConsole)
            System.setProperty(SimpleLoggerConfiguration.CONFIGURATION_FILE_KEY,
                    "default-log-dbus-daemon-console.properties");
        else
            System.setProperty(SimpleLoggerConfiguration.CONFIGURATION_FILE_KEY, "default-log-dbus-daemon.properties");

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        log = LoggerFactory.getLogger(DBusD.class);

        Preferences addrpref = null;
        String addrkey = "dbusAddress";
        String addrfilekey = null;

        if (addresspref.isPresent()) {
            var prefspec = addresspref.get();
            if (prefspec.equals("default")) {
                addrkey = "dbusAddress";
                if (addressuserpref)
                    addrpref = Preferences.userNodeForPackage(DBusDaemon.class);
                else
                    addrpref = Preferences.systemNodeForPackage(DBusDaemon.class);
            } else {
                int idx = prefspec.indexOf(':');
                String node = idx == -1 ? prefspec : prefspec.substring(0, idx);
                addrkey = idx == -1 ? "dbusAddress" : prefspec.substring(idx + 1);
                addrpref = Preferences.systemRoot().node(node);
            }
        }

        String addr;
        // generate a random address if none specified
        if (address.isEmpty() && (unix || !tcp)) {
            addr = TransportBuilder.createDynamicSession("UNIX", true);
        } else if (address.isEmpty() && tcp) {
            addr = TransportBuilder.createDynamicSession("TCP", true);
        } else
            addr = address.get();

        // developer mode
        if (Files.exists(Paths.get("pom.xml"))) {
            if (addressfile.isEmpty()) {
                var conf = Paths.get("conf");
                Files.createDirectories(conf);
                addressfile = Optional.of(conf.resolve("dbus.properties"));
            }
            System.out.println("**** Developer mode. Other developer mode components will look for dbus.properties @ "
                    + addressfile.get() + " ****");
            printaddress = true;
            addrfilekey = "address";
        } else
            addrfilekey = addressfileKey.orElse(null);

        var publicAddress = BusAddress.of(addr);
        publicAddress.removeParameter("listen");

        // print address to stdout
        if (printaddress) {
            System.out.println(publicAddress);
        }

        // print address to file
        if (addressfile.isPresent()) {
            saveFile(publicAddress.toString(), addressfile.get(), insecure, addrfilekey);
        }

        // print PID to file
        if (pidfile.isPresent()) {
            saveFile(String.valueOf(ProcessHandle.current().pid()), pidfile.get(), insecure, null);
        }

        // print address to a preference key
        if (null != addrpref) {
            addrpref.put(addrkey, publicAddress.toString());
        }

        // start the daemon
        log.info("Binding to {}", addr);
        try (EmbeddedDBusDaemon daemon = new EmbeddedDBusDaemon(BusAddress.of(addr))) {
            daemon.setSaslAuthMode(authMode);
            daemon.startInForeground();
        }

        return null;
    }

    private static void saveFile(String _data, Path file, boolean _insecure, String key) throws IOException {
        try (var w = new PrintWriter(Files.newBufferedWriter(file))) {
            if (key == null)
                w.println(_data);
            else {
                var props = new Properties();
                props.put(key, _data);
                props.store(w, "DBusD");
            }
        }
        var asFile = file.toFile();
        asFile.deleteOnExit();
        if (_insecure) {
            asFile.setReadable(true, false);
            asFile.setWritable(true, false);
        }
    }

}
