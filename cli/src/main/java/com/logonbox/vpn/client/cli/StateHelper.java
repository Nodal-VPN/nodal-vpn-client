package com.logonbox.vpn.client.cli;

import com.logonbox.vpn.client.common.Connection.Mode;
import com.logonbox.vpn.client.common.ConnectionStatus.Type;
import com.logonbox.vpn.client.common.VpnManager;
import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.dbus.VpnConnection;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class StateHelper implements Closeable {
	static Logger log = LoggerFactory.getLogger(StateHelper.class);
	
	public interface StateChange {
		void state(Type state, Mode mode) throws Exception;
	}

	private final IVpnConnection connection;
    private final List<AutoCloseable> handles;
    private final Map<Type, StateChange> onState = new HashMap<>();
	private final Object lock = new Object();
	
    private Type currentState;
	private boolean interrupt;
	private Exception error;

	public StateHelper(VpnConnection connection, VpnManager<VpnConnection> manager) throws DBusException {
        this.connection = connection;
        currentState = Type.valueOf(connection.getStatus());
        handles = Arrays.asList(
                manager.onDisconnecting((conx, reason) -> changed(conx)),
                manager.onDisconnected((conx, reason) -> changed(conx)),
                manager.onFailure((conx, message, cause, trace) -> changed(conx)),
                manager.onConnecting(conx -> changed(conx)), 
                manager.onConnected(conx -> changed(conx)),
                manager.onAuthorize((conx, uri, mode, authMethods) -> changed(conx)),
                manager.onTemporarilyOffline((conx, reason) -> changed(conx)));
	}

    protected void changed(VpnConnection changedConnection) {
        if(changedConnection.equals(connection)) {
              stateChange();
          }
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
		handles.forEach(h -> {
            try {
                h.close();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to close handle.");
            }
        });
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
