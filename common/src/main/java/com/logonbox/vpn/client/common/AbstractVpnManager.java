package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.api.IVpnConnection;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class AbstractVpnManager<CONX extends IVpnConnection> implements VpnManager<CONX> {

    public final static File CLIENT_HOME = new File(
            System.getProperty("user.home") + File.separator + ".logonbox-vpn-client");
    public final static File CLIENT_CONFIG_HOME = new File(CLIENT_HOME, "conf");
    
    protected final List<Runnable> onConnectionAdding = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnectionAdded = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnectionRemoving = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<Long>> onConnectionRemoved = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnectionUpdated = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnectionUpdating = Collections.synchronizedList(new ArrayList<>());
    protected final List<BiConsumer<ConfigurationItem<?>, String>> onGlobalConfigChanged = Collections.synchronizedList(new ArrayList<>());
    protected final List<Runnable> onVpnGone = Collections.synchronizedList(new ArrayList<>());
    protected final List<Runnable> onConfirmedExit = Collections.synchronizedList(new ArrayList<>());
    protected final List<Runnable> onVpnAvailable = Collections.synchronizedList(new ArrayList<>());
    protected final List<Authorize<CONX>> onAuthorize = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnecting= Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onConnected= Collections.synchronizedList(new ArrayList<>());
    protected final List<BiConsumer<CONX, String>> onDisconnecting = Collections.synchronizedList(new ArrayList<>());
    protected final List<BiConsumer<CONX, String>> onDisconnected = Collections.synchronizedList(new ArrayList<>());
    protected final List<BiConsumer<CONX, String>> onTemporarilyOffline = Collections.synchronizedList(new ArrayList<>());
    protected final List<Consumer<CONX>> onBlocked = Collections.synchronizedList(new ArrayList<>());
    protected final List<Failure<CONX>> onFailure = Collections.synchronizedList(new ArrayList<>());
    
    @Override
    public final Handle onConnectionAdded(Consumer<CONX> callback) {
        onConnectionAdded.add(callback);
        return () -> onConnectionAdded.remove(callback);
    }

    @Override
    public final Handle onConnectionAdding(Runnable callback) {
        onConnectionAdding.add(callback);
        return () -> onConnectionAdding.remove(callback);
    }

    @Override
    public final Handle onConnectionUpdating(Consumer<CONX> callback) {
        onConnectionUpdating.add(callback);
        return () -> onConnectionUpdating.remove(callback);
    }

    @Override
    public final Handle onConnectionUpdated(Consumer<CONX> callback) {
        onConnectionUpdated.add(callback);
        return () -> onConnectionUpdated.remove(callback);
    }

    @Override
    public final Handle onConnectionRemoved(Consumer<Long> callback) {
        onConnectionRemoved.add(callback);
        return () -> onConnectionRemoved.remove(callback);
    }

    @Override
    public final Handle onConnectionRemoving(Consumer<CONX> callback) {
        onConnectionRemoving.add(callback);
        return () -> onConnectionRemoving.remove(callback);
    }

    @Override
    public final Handle onGlobalConfigChanged(BiConsumer<ConfigurationItem<?>, String> callback) {
        onGlobalConfigChanged.add(callback);
        return () -> onGlobalConfigChanged.remove(callback);
    }

    @Override
    public final Handle onVpnGone(Runnable callback) {
        onVpnGone.add(callback);
        return () -> onVpnGone.remove(callback);
    }

    @Override
    public final Handle onConfirmedExit(Runnable callback) {
        onConfirmedExit.add(callback);
        return () -> onConfirmedExit.remove(callback);
    }

    @Override
    public final Handle onVpnAvailable(Runnable callback) {
        onVpnAvailable.add(callback);
        return () -> onVpnAvailable.remove(callback);
    }

    @Override
    public final Handle onAuthorize(Authorize<CONX> callback) {
        onAuthorize.add(callback);
        return () -> onAuthorize.remove(callback);
    }

    @Override
    public final Handle onConnecting(Consumer<CONX> callback) {
        onConnecting.add(callback);
        return () -> onConnecting.remove(callback);
    }

    @Override
    public final Handle onConnected(Consumer<CONX> callback) {
        onConnected.add(callback);
        return () -> onConnected.remove(callback);
    }

    @Override
    public final Handle onDisconnecting(BiConsumer<CONX, String> callback) {
        onDisconnecting.add(callback);
        return () -> onDisconnecting.remove(callback);
    }

    @Override
    public final Handle onDisconnected(BiConsumer<CONX, String> callback) {
        onDisconnected.add(callback);
        return () -> onDisconnected.remove(callback);
    }

    @Override
    public final Handle onTemporarilyOffline(BiConsumer<CONX, String> callback) {
        onTemporarilyOffline.add(callback);
        return () -> onTemporarilyOffline.remove(callback);
    }

    @Override
    public final Handle onBlocked(Consumer<CONX> callback) {
        onBlocked.add(callback);
        return () -> onBlocked.remove(callback);
    }

    @Override
    public final Handle onFailure(Failure<CONX> callback) {
        onFailure.add(callback);
        return () -> onFailure.remove(callback);
    }

    public final List<Runnable> onConnectionAdding() {
        return onConnectionAdding;
    }

    public final List<Consumer<CONX>> onConnectionAdded() {
        return onConnectionAdded;
    }

    public final List<Consumer<CONX>> onConnectionRemoving() {
        return onConnectionRemoving;
    }

    public final List<Consumer<Long>> onConnectionRemoved() {
        return onConnectionRemoved;
    }

    public final List<Consumer<CONX>> onConnectionUpdated() {
        return onConnectionUpdated;
    }

    public final List<Consumer<CONX>> onConnectionUpdating() {
        return onConnectionUpdating;
    }

    public final List<BiConsumer<ConfigurationItem<?>, String>> onGlobalConfigChanged() {
        return onGlobalConfigChanged;
    }

    public final List<Runnable> onVpnGone() {
        return onVpnGone;
    }

    public final List<Runnable> onConfirmedExit() {
        return onConfirmedExit;
    }

    public final List<Runnable> onVpnAvailable() {
        return onVpnAvailable;
    }

    public final List<Authorize<CONX>> onAuthorize() {
        return onAuthorize;
    }

    public final List<Consumer<CONX>> onConnecting() {
        return onConnecting;
    }

    public final List<Consumer<CONX>> onConnected() {
        return onConnected;
    }

    public final List<BiConsumer<CONX, String>> onDisconnecting() {
        return onDisconnecting;
    }

    public final List<BiConsumer<CONX, String>> onDisconnected() {
        return onDisconnected;
    }

    public final List<BiConsumer<CONX, String>> onTemporarilyOffline() {
        return onTemporarilyOffline;
    }

    public final List<Consumer<CONX>> onBlocked() {
        return onBlocked;
    }

    public final List<Failure<CONX>> onFailure() {
        return onFailure;
    }
}
