package com.logonbox.vpn.client.common.api;

import java.io.Closeable;
import java.util.function.Consumer;

public interface PowerManager extends Closeable {

    void onSuspend(Consumer<Boolean> suspendAction);
    
    @Override
    void close();
}
