package com.logonbox.vpn.client.cli;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

public class StateHelper implements Closeable {
	static Logger log = LoggerFactory.getLogger(StateHelper.class);
	
	public interface StateChange {
		void state(Type state, Mode mode) throws Exception;
	}

	private DBusConnection bus;
	private VPNConnection connection;
	private Type currentState;
	private Object lock = new Object();
	private boolean interrupt;
	private Map<Type, StateChange> onState = new HashMap<>();
	private Exception error;
	private final List<AutoCloseable> handles;

	public StateHelper(VPNConnection connection, DBusConnection bus) throws DBusException {
		this.connection = connection;
		this.bus = bus;
		currentState = Type.valueOf(connection.getStatus());
		handles = Arrays.asList(
				bus.addSigHandler(VPNConnection.Disconnecting.class, connection, sig -> stateChange()),
				bus.addSigHandler(VPNConnection.Disconnected.class, connection, sig -> stateChange()),
				bus.addSigHandler(VPNConnection.Failed.class, connection, sig -> stateChange()),
				bus.addSigHandler(VPNConnection.Connecting.class, connection, sig -> stateChange()),
				bus.addSigHandler(VPNConnection.Connected.class, connection, sig -> stateChange()),
				bus.addSigHandler(VPNConnection.Authorize.class, connection, sig -> stateChange()),
				bus.addSigHandler(VPNConnection.TemporarilyOffline.class, connection, sig -> stateChange())
		);
	}

	public Type waitForStateNot(Type... state) throws InterruptedException {
		return waitForStateNot(-1, state);
	}

	public Type waitForStateNot(long timeout, Type... state) throws InterruptedException {
		return waitForStateNot(timeout, TimeUnit.MILLISECONDS, state);
	}

	public Type waitForStateNot(long timeout, TimeUnit unit, Type... state) throws InterruptedException {
		try {
			if (timeout < 0) {
				while (isState(state) && !interrupt) {
					synchronized (lock) {
						lock.wait();
					}
				}
			} else {
				long ms = unit.toMillis(timeout);
				while (isState(state) && ms > 0 && !interrupt) {
					long started = System.currentTimeMillis();
					synchronized (lock) {
						lock.wait(unit.toMillis(ms));
					}
					ms -= System.currentTimeMillis() - started;
				}
				if (ms <= 0)
					throw new InterruptedException("Timeout.");
			}
			return currentState;
		} finally {
			interrupt = false;
		}
	}

	public Type waitForState(Type... state) throws InterruptedException {
		return waitForState(-1, state);
	}

	public Type waitForState(long timeout, Type... state) throws InterruptedException {
		return waitForState(timeout, TimeUnit.MILLISECONDS, state);
	}

	public Type waitForState(long timeout, TimeUnit unit, Type... state) throws InterruptedException {
		try {
			if (timeout < 0) {
				while (!isState(state) && !interrupt) {
					synchronized (lock) {
						lock.wait();
					}
				}
			} else {
				long ms = unit.toMillis(timeout);
				while (!isState(state) && timeout > 0 && !interrupt) {
					long started = System.currentTimeMillis();
					synchronized (lock) {
						lock.wait(ms);
					}
					ms -= System.currentTimeMillis() - started;
				}
				if (ms < 0)
					throw new InterruptedException("Timeout.");
			}
			if(error != null) {
				if(error instanceof RuntimeException)
					throw (RuntimeException)error;
				else
					throw new IllegalStateException(error);
			}
			return currentState;
		}

		finally {
			error = null;
			interrupt = false;
		}
	}

	@Override
	public void close() throws IOException {
		onState.clear();
		for(var h : handles) {
			try {
				h.close();
			} catch (Exception e) {
				throw new IOException("Failed to remove signal handler.", e);
			}
		}
	}

	boolean isState(Type... state) {
		for (Type s : state)
			if (s == currentState)
				return true;
		return false;
	}

	void stateChange() {
		synchronized (lock) {
			Type newState = Type.valueOf(connection.getStatus());
			if(!Objects.equals(currentState, newState)) { 
				log.info("State change from {} to {} [{}]", currentState, newState, StateHelper.this.hashCode());
				currentState = newState;
				try {
					if(onState.containsKey(currentState))  {
						try {
							onState.get(currentState).state(currentState, Mode.valueOf(connection.getMode()));
						} catch (Exception e) {
							error = e;
							interrupt = true;
							log.debug("Failed state change.", e);
						}
					}
				}
				finally {
					lock.notifyAll();
				}
			}
		}
	}

	public void start(Type state) {
		currentState = state;
		error = null;
		interrupt = false;
	}
	
	public void on(Type type, StateChange run) {
		synchronized (lock) {
			onState.put(type, run);
		}
	}

	public void interrupt() {
		synchronized (lock) {
			interrupt = true;
			lock.notifyAll();
		}
	}
}
