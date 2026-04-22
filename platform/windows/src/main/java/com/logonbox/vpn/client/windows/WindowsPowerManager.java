package com.logonbox.vpn.client.windows;

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
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class WindowsPowerManager implements PowerManager, Runnable {

    // Windows message constants
    private static final int WM_CLOSE = 0x0010;
    private static final int WM_ENDSESSION = 0x0016;
    private static final int WM_DESTROY = 0x0002;
    private static final int WM_POWERBROADCAST = 0x0218;
    private static final int PBT_APMSUSPEND = 0x0004;

    // WNDCLASSW field offsets (64-bit)
    private static final long WNDCLASS_STYLE = 0;        // UINT (4)
    // 4 bytes padding
    private static final long WNDCLASS_WNDPROC = 8;      // WNDPROC (8)
    private static final long WNDCLASS_CLSEXTRA = 16;    // int (4)
    private static final long WNDCLASS_WNDEXTRA = 20;    // int (4)
    private static final long WNDCLASS_HINSTANCE = 24;   // HINSTANCE (8)
    private static final long WNDCLASS_HICON = 32;       // HICON (8)
    private static final long WNDCLASS_HCURSOR = 40;     // HCURSOR (8)
    private static final long WNDCLASS_HBRBACKGROUND = 48; // HBRUSH (8)
    private static final long WNDCLASS_MENUNAME = 56;    // LPCWSTR (8)
    private static final long WNDCLASS_CLASSNAME = 64;   // LPCWSTR (8)
    private static final long WNDCLASS_SIZE = 72;

    private static final MethodHandle RegisterClassW;
    private static final MethodHandle CreateWindowExW;
    private static final MethodHandle GetMessageW;
    private static final MethodHandle TranslateMessage;
    private static final MethodHandle DispatchMessageW;
    private static final MethodHandle DefWindowProcW;
    private static final MethodHandle DestroyWindow;
    private static final MethodHandle PostQuitMessage;
    private static final MethodHandle PostMessageW;
    private static final MethodHandle GetModuleHandleW;

    static {
        var linker = Linker.nativeLinker();
        var user32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
        var kernel32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

        RegisterClassW = linker.downcallHandle(
            user32.find("RegisterClassW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS)
        );

        CreateWindowExW = linker.downcallHandle(
            user32.find("CreateWindowExW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,   // dwExStyle
                ValueLayout.ADDRESS,    // lpClassName
                ValueLayout.ADDRESS,    // lpWindowName
                ValueLayout.JAVA_INT,   // dwStyle
                ValueLayout.JAVA_INT,   // x
                ValueLayout.JAVA_INT,   // y
                ValueLayout.JAVA_INT,   // nWidth
                ValueLayout.JAVA_INT,   // nHeight
                ValueLayout.ADDRESS,    // hWndParent
                ValueLayout.ADDRESS,    // hMenu
                ValueLayout.ADDRESS,    // hInstance
                ValueLayout.ADDRESS     // lpParam
            )
        );

        GetMessageW = linker.downcallHandle(
            user32.find("GetMessageW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );

        TranslateMessage = linker.downcallHandle(
            user32.find("TranslateMessage").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );

        DispatchMessageW = linker.downcallHandle(
            user32.find("DispatchMessageW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );

        DefWindowProcW = linker.downcallHandle(
            user32.find("DefWindowProcW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );

        DestroyWindow = linker.downcallHandle(
            user32.find("DestroyWindow").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );

        PostQuitMessage = linker.downcallHandle(
            user32.find("PostQuitMessage").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)
        );

        PostMessageW = linker.downcallHandle(
            user32.find("PostMessageW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );

        GetModuleHandleW = linker.downcallHandle(
            kernel32.find("GetModuleHandleW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
    }

    private Consumer<Boolean> onSuspendAction;
    private Arena arena;
    private volatile MemorySegment hwnd;

    public WindowsPowerManager() {
        arena = Arena.ofShared();
    }

    @Override
    public void run() {
        try {
            var hInstance = (MemorySegment) GetModuleHandleW.invoke(MemorySegment.NULL);

            // Create WndProc upcall stub
            var wndProcDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,   // LRESULT
                ValueLayout.ADDRESS,     // HWND
                ValueLayout.JAVA_INT,    // UINT uMsg
                ValueLayout.JAVA_LONG,   // WPARAM
                ValueLayout.JAVA_LONG    // LPARAM
            );

            var wndProcHandle = MethodHandles.lookup().findVirtual(
                WindowsPowerManager.class, "windowProc",
                MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class)
            ).bindTo(this);

            var wndProcStub = Linker.nativeLinker().upcallStub(wndProcHandle, wndProcDesc, arena);

            // Populate WNDCLASSW structure
            var className = arena.allocateFrom("NodalVPNPowerMgr\0", StandardCharsets.UTF_16LE);
            var wndClass = arena.allocate(WNDCLASS_SIZE);
            wndClass.set(ValueLayout.JAVA_INT, WNDCLASS_STYLE, 0);
            wndClass.set(ValueLayout.ADDRESS, WNDCLASS_WNDPROC, wndProcStub);
            wndClass.set(ValueLayout.JAVA_INT, WNDCLASS_CLSEXTRA, 0);
            wndClass.set(ValueLayout.JAVA_INT, WNDCLASS_WNDEXTRA, 0);
            wndClass.set(ValueLayout.ADDRESS, WNDCLASS_HINSTANCE, hInstance);
            wndClass.set(ValueLayout.ADDRESS, WNDCLASS_HICON, MemorySegment.NULL);
            wndClass.set(ValueLayout.ADDRESS, WNDCLASS_HCURSOR, MemorySegment.NULL);
            wndClass.set(ValueLayout.ADDRESS, WNDCLASS_HBRBACKGROUND, MemorySegment.NULL);
            wndClass.set(ValueLayout.ADDRESS, WNDCLASS_MENUNAME, MemorySegment.NULL);
            wndClass.set(ValueLayout.ADDRESS, WNDCLASS_CLASSNAME, className);

            var atom = (short) RegisterClassW.invoke(wndClass);
            if (atom == 0) {
                throw new RuntimeException("RegisterClassW failed");
            }

            // Create message-only window (HWND_MESSAGE = -3)
            hwnd = (MemorySegment) CreateWindowExW.invoke(
                0,                                      // dwExStyle
                className,                              // lpClassName
                MemorySegment.NULL,                     // lpWindowName
                0,                                      // dwStyle
                0, 0, 0, 0,                            // x, y, width, height
                MemorySegment.ofAddress(-3L),           // hWndParent = HWND_MESSAGE
                MemorySegment.NULL,                     // hMenu
                hInstance,                              // hInstance
                MemorySegment.NULL                      // lpParam
            );

            if (hwnd.equals(MemorySegment.NULL)) {
                throw new RuntimeException("CreateWindowExW failed");
            }

            // Message loop
            var msg = arena.allocate(64); // MSG structure (48 bytes + padding)
            while (true) {
                int ret = (int) GetMessageW.invoke(msg, MemorySegment.NULL, 0, 0);
                if (ret == 0 || ret == -1) break;
                TranslateMessage.invoke(msg);
                DispatchMessageW.invoke(msg);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to run power event monitoring loop", e);
        }
    }

    @SuppressWarnings("unused")
    private long windowProc(MemorySegment hwnd, int uMsg, long wParam, long lParam) {
        try {
            switch (uMsg) {
                case WM_POWERBROADCAST:
                    if (wParam == PBT_APMSUSPEND) {
                        handleSuspendOrShutdown(false);
                    }
                    return 1; // TRUE - message processed
                case WM_ENDSESSION:
                    if (wParam != 0) { // session is ending
                        handleSuspendOrShutdown(true);
                    }
                    return 0;
                case WM_CLOSE:
                    DestroyWindow.invoke(hwnd);
                    return 0;
                case WM_DESTROY:
                    PostQuitMessage.invoke(0);
                    return 0;
                default:
                    return (long) DefWindowProcW.invoke(hwnd, uMsg, wParam, lParam);
            }
        } catch (Throwable e) {
            return 0;
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
            if (hwnd != null && !hwnd.equals(MemorySegment.NULL)) {
                PostMessageW.invoke(hwnd, WM_CLOSE, 0L, 0L);
            }
        } catch (Throwable e) {
            // ignore
        }
        // Arena will be closed after message loop exits and resources are no longer needed
        // Don't close arena here as the message loop thread may still be using it
    }
}