package com.logonbox.vpn.client.linux;

import com.logonbox.vpn.client.common.api.PowerManager;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.DBusMatchRule;
import org.freedesktop.dbus.messages.DBusSignal;


import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

public class LinuxPowerManager implements PowerManager, Runnable {
    
    public static void main(String[] args) {
        try (LinuxPowerManager powerManager = new LinuxPowerManager()) {
            powerManager.onSuspend((shutdown) -> System.out.println(shutdown ? "System is shutting down..." : "System is suspending..."));
            powerManager.run();
        }
    }

    @DBusInterfaceName("org.freedesktop.login1.Manager")
    public interface Login1Manager extends DBusInterface {
        
        @Reflectable
        @TypeReflect(methods = true, constructors = true)
        public static class PrepareForSleep extends DBusSignal {
            private final boolean going;
            
            public PrepareForSleep(String path, boolean going) throws DBusException {
                super(path, going);
                this.going = going;
            }
            
            public boolean isGoing() {
                return going;
            }
        }
    }
    
    private static final String LOGIN1_PATH = "/org/freedesktop/login1";
    
    private Consumer<Boolean> onSuspendAction;
    private DBusConnection dbusConnection;
    private DBusSigHandler<DBusSignal> sleepHandler;
    private DBusSigHandler<DBusSignal> shutdownHandler;
    private Thread runThread;
    
    public LinuxPowerManager() {
        try {
            dbusConnection = DBusConnectionBuilder.forSystemBus().build();
            
            sleepHandler = signal -> {
                try {
                    var params = signal.getParameters();
                    if (params != null && params.length > 0 && Boolean.TRUE.equals(params[0])) {
                        handleSuspend(false);
                    }
                } catch (DBusException e) {
                    // ignore
                }
            };
            var sleepRule = new DBusMatchRule("signal", "org.freedesktop.login1.Manager", "PrepareForSleep", LOGIN1_PATH);
            dbusConnection.addGenericSigHandler(sleepRule, sleepHandler);
            
            shutdownHandler = signal -> {
                try {
                    var params = signal.getParameters();
                    if (params != null && params.length > 0 && Boolean.TRUE.equals(params[0])) {
                        handleSuspend(true);
                    }
                } catch (DBusException e) {
                    // ignore
                }
            };
            var shutdownRule = new DBusMatchRule("signal", "org.freedesktop.login1.Manager", "PrepareForShutdown", LOGIN1_PATH);
            dbusConnection.addGenericSigHandler(shutdownRule, shutdownHandler);
        } catch (DBusException e) {
            throw new RuntimeException("Failed to connect to system D-Bus for power event monitoring", e);
        }
    }
    
    public void run() {
        runThread = Thread.currentThread();
        // The D-Bus connection handles signal dispatching on its own threads.
        // This method can be used if additional blocking behavior is needed.
        try {
            Thread.sleep(Duration.ofDays(Short.MAX_VALUE).toMillis());
        } catch (InterruptedException e) {
        }
    }
    
    private void handleSuspend(boolean isShutdown) {
        if (onSuspendAction != null) {
            onSuspendAction.accept(isShutdown);
        }
    }
    
    @Override
    public void onSuspend(Consumer<Boolean> suspendAction) {
        this.onSuspendAction = suspendAction;
    }

    @Override
    public void close() {
        try {
            if (dbusConnection != null) {
                try {
                    if (sleepHandler != null) {
                        var sleepRule = new DBusMatchRule("signal", "org.freedesktop.login1.Manager", "PrepareForSleep", LOGIN1_PATH);
                        dbusConnection.removeGenericSigHandler(sleepRule, sleepHandler);
                    }
                    if (shutdownHandler != null) {
                        var shutdownRule = new DBusMatchRule("signal", "org.freedesktop.login1.Manager", "PrepareForShutdown", LOGIN1_PATH);
                        dbusConnection.removeGenericSigHandler(shutdownRule, shutdownHandler);
                    }
                } catch (DBusException e) {
                    // ignore
                }
                try {
                    dbusConnection.close();
                } catch (IOException e) {
                    // ignore
                }
                dbusConnection = null;
            }
        }
        finally {
            if (runThread != null) {
                runThread.interrupt();
            }
        }
    }

}