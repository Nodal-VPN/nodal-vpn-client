package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractUpdateService implements UpdateService {

	static Logger log = LoggerFactory.getLogger(AbstractUpdateService.class);

	private List<Listener> listeners = new ArrayList<>();
	private long deferUpdatesUntil;
	private boolean updating;
	private String availableVersion;
	private ScheduledFuture<?> checkTask;

	protected AbstractDBusClient context;
	protected ScheduledExecutorService scheduler;

	protected AbstractUpdateService(AbstractDBusClient context) {
		this.context = context;
		scheduler = Executors.newScheduledThreadPool(1);
		rescheduleCheck(TimeUnit.SECONDS.toMillis(10));
	}

	@Override
	public void shutdown() {
		scheduler.shutdown();
	}

	@Override
	public final void addListener(Listener listener) {
		listeners.add(listener);
	}

	@Override
	public final boolean isNeedsUpdating() {
		return availableVersion != null;
	}

	@Override
	public final boolean isUpdating() {
		return updating;
	}

	@Override
	public final String getAvailableVersion() {
		return availableVersion == null ? context.getVersion() : availableVersion;
	}

	@Override
	public final void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	@Override
	public final boolean isUpdatesEnabled() {
		return "false".equals(System.getProperty("hypersocket.development.noUpdates", "false"));
	}

	@Override
	public final void update() throws IOException {
		if (!isNeedsUpdating()) {
			throw new IllegalStateException("An update is not required.");
		}
		update(false);
	}

	@Override
	public final void deferUpdate() {
		long dayMs = TimeUnit.DAYS.toMillis(1);
		deferUpdatesUntil = ((System.currentTimeMillis() / dayMs) * dayMs) + dayMs;
	}

	@Override
	public final void checkForUpdate() throws IOException {
		deferUpdatesUntil = 0;
		update(true);
	}

	protected void rescheduleCheck(long nonDeferredDelay) {
		if (checkTask != null) {
			checkTask.cancel(false);
		}
		if (deferUpdatesUntil == 0) {
			if (nonDeferredDelay == 0) {
				/* Ordinary schedule, check at noon + some random minutes */
				long day = TimeUnit.DAYS.toMillis(1);
				long nowDay = System.currentTimeMillis() / day;
				checkTask = scheduler.schedule(() -> timedCheck(),
						nowDay + day + TimeUnit.HOURS.toMillis(12)
								+ (long) (Math.random() * 3.0d * (double) TimeUnit.HOURS.toMillis(3)),
						TimeUnit.MILLISECONDS);
			} else {
				checkTask = scheduler.schedule(() -> timedCheck(), nonDeferredDelay, TimeUnit.MILLISECONDS);
			}

		} else {
			/* Deferred until fixed time */
			checkTask = scheduler.schedule(() -> timedCheck(),
					Math.max(1, deferUpdatesUntil - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
		}
	}

	protected void timedCheck() {
		try {
			update(true);
		} catch (Exception e) {
			log.error("Failed to automatically check for updates.", e);
		}
	}

	protected final void update(boolean check) throws IOException {
		if (!isUpdatesEnabled()) {
			log.info("Updates disabled.");
			setAvailableVersion(null);
		} else {
			if (deferUpdatesUntil == 0 || System.currentTimeMillis() >= deferUpdatesUntil) {
				deferUpdatesUntil = 0;
				updating = true;
				try {
					setAvailableVersion(doUpdate(check));
				} finally {
					updating = false;
				}
			} else {
				log.info(String.format("Updates deferred until %s",
						DateFormat.getDateTimeInstance().format(new Date(deferUpdatesUntil))));
			}
		}
	}

	protected void setAvailableVersion(String version) {
		if (!Objects.equals(availableVersion, version)) {
			this.availableVersion = version;
			fireStateChange();
		}
	}

	protected void fireStateChange() {
		for (int i = listeners.size() - 1; i >= 0; i--) {
			listeners.get(i).stateChanged();
		}
	}

	protected abstract String doUpdate(boolean check) throws IOException;

	private boolean isNightly(String phase) {
		return phase.startsWith("nightly");
	}

	@Override
	public final String[] getPhases() {
		List<String> l = new ArrayList<>();
		for (String p : new String[] { "nightly", "ea", "stable" }) {
			if (!isNightly(p) || (Boolean.getBoolean("logonbox.vpn.updates.nightly")
					|| Boolean.getBoolean("hypersocket.development"))) {
				l.add(p);
			}
		}
		return l.toArray(new String[0]);
	}

}
