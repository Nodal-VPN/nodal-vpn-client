package com.logonbox.vpn.client.macos;

import com.logonbox.vpn.client.common.api.PowerManager;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

public class MacOSPowerManager implements PowerManager, Runnable {

    // IOKit power message constants
    private static final int kIOMessageCanSystemSleep = 0xe0000270;
    private static final int kIOMessageSystemWillSleep = 0xe0000280;
    private static final int kIOMessageSystemHasPoweredOn = 0xe0000300;

    private static final MethodHandle IORegisterForSystemPower;
    private static final MethodHandle IODeregisterForSystemPower;
    private static final MethodHandle IOAllowPowerChange;
    private static final MethodHandle IONotificationPortGetRunLoopSource;
    private static final MethodHandle IONotificationPortDestroy;
    private static final MethodHandle CFRunLoopGetCurrent;
    private static final MethodHandle CFRunLoopAddSource;
    private static final MethodHandle CFRunLoopRun;
    private static final MethodHandle CFRunLoopStop;
    private static final MemorySegment kCFRunLoopDefaultMode;

    static {
        var linker = Linker.nativeLinker();
        var iokit = SymbolLookup.libraryLookup("/System/Library/Frameworks/IOKit.framework/IOKit", Arena.global());
        var cf = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());

        IORegisterForSystemPower = linker.downcallHandle(
            iokit.find("IORegisterForSystemPower").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        IODeregisterForSystemPower = linker.downcallHandle(
            iokit.find("IODeregisterForSystemPower").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );

        IOAllowPowerChange = linker.downcallHandle(
            iokit.find("IOAllowPowerChange").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
        );

        IONotificationPortGetRunLoopSource = linker.downcallHandle(
            iokit.find("IONotificationPortGetRunLoopSource").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        IONotificationPortDestroy = linker.downcallHandle(
            iokit.find("IONotificationPortDestroy").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );

        CFRunLoopGetCurrent = linker.downcallHandle(
            cf.find("CFRunLoopGetCurrent").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS)
        );

        CFRunLoopAddSource = linker.downcallHandle(
            cf.find("CFRunLoopAddSource").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        CFRunLoopRun = linker.downcallHandle(
            cf.find("CFRunLoopRun").orElseThrow(),
            FunctionDescriptor.ofVoid()
        );

        CFRunLoopStop = linker.downcallHandle(
            cf.find("CFRunLoopStop").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );

        kCFRunLoopDefaultMode = cf.find("kCFRunLoopDefaultMode").orElseThrow()
            .reinterpret(ValueLayout.ADDRESS.byteSize())
            .get(ValueLayout.ADDRESS, 0);
    }

    private Consumer<Boolean> onSuspendAction;
    private Arena arena;
    private int rootPort;
    private int notifierObject;
    private MemorySegment notifyPortRef;
    private volatile MemorySegment runLoop;

    public MacOSPowerManager() {
        arena = Arena.ofShared();
    }

    @Override
    public void run() {
        try {
            var notifyPortPtr = arena.allocate(ValueLayout.ADDRESS);
            var notifierPtr = arena.allocate(ValueLayout.JAVA_INT);

            var callbackDesc = FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,   // refCon
                ValueLayout.JAVA_INT,  // service (io_service_t / mach_port_t)
                ValueLayout.JAVA_INT,  // messageType
                ValueLayout.ADDRESS    // messageArgument
            );

            var callbackHandle = MethodHandles.lookup().findVirtual(
                MacOSPowerManager.class, "powerCallback",
                MethodType.methodType(void.class, MemorySegment.class, int.class, int.class, MemorySegment.class)
            ).bindTo(this);

            var callbackStub = Linker.nativeLinker().upcallStub(callbackHandle, callbackDesc, arena);

            rootPort = (int) IORegisterForSystemPower.invoke(
                MemorySegment.NULL, notifyPortPtr, callbackStub, notifierPtr
            );

            if (rootPort == 0) {
                throw new RuntimeException("IORegisterForSystemPower failed");
            }

            notifyPortRef = notifyPortPtr.get(ValueLayout.ADDRESS, 0);
            notifierObject = notifierPtr.get(ValueLayout.JAVA_INT, 0);

            var runLoopSource = (MemorySegment) IONotificationPortGetRunLoopSource.invoke(notifyPortRef);
            runLoop = (MemorySegment) CFRunLoopGetCurrent.invoke();
            CFRunLoopAddSource.invoke(runLoop, runLoopSource, kCFRunLoopDefaultMode);

            CFRunLoopRun.invoke();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to run power event monitoring loop", e);
        }
    }

    @SuppressWarnings("unused")
    private void powerCallback(MemorySegment refCon, int service, int messageType, MemorySegment messageArgument) {
        switch (messageType) {
            case kIOMessageCanSystemSleep:
                try {
                    IOAllowPowerChange.invoke(rootPort, messageArgument.address());
                } catch (Throwable e) {
                    // ignore
                }
                break;
            case kIOMessageSystemWillSleep:
                handleSuspendOrShutdown(false);
                try {
                    IOAllowPowerChange.invoke(rootPort, messageArgument.address());
                } catch (Throwable e) {
                    // ignore
                }
                break;
            case kIOMessageSystemHasPoweredOn:
                // System woke from sleep - no action needed
                break;
        }
    }

    private void handleSuspendOrShutdown(boolean isShutdown) {
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
            if (runLoop != null && !runLoop.equals(MemorySegment.NULL)) {
                CFRunLoopStop.invoke(runLoop);
                runLoop = null;
            }
        } catch (Throwable e) {
            // ignore
        }
        try {
            if (notifierObject != 0) {
                IODeregisterForSystemPower.invoke(notifierObject);
                notifierObject = 0;
            }
        } catch (Throwable e) {
            // ignore
        }
        try {
            if (notifyPortRef != null && !notifyPortRef.equals(MemorySegment.NULL)) {
                IONotificationPortDestroy.invoke(notifyPortRef);
                notifyPortRef = null;
            }
        } catch (Throwable e) {
            // ignore
        }
        if (arena != null) {
            arena.close();
            arena = null;
        }
    }
}