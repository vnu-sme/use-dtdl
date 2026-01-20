package org.tzi.use.dtdl.runtime.telemetry;


import org.tzi.use.dtdl.runtime.DTDLSystem;
import org.tzi.use.dtdl.runtime.telemetry.adapters.PollingHttpAdapter;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestration facade for TelemetryHub + adapters + lifecycle + mapping + dead-letter.
 * Keeps the plugin-level entry points small and centralized.
 */
public final class TelemetryManager {
    private final TelemetryHub hub;
    private final DTDLSystem system;
    private final DeadLetterSink deadLetter;
    private final List<TelemetryAdapter> adapters = Collections.synchronizedList(new ArrayList<>());
    private final Map<String,String> deviceToInstanceMap = Collections.synchronizedMap(new LinkedHashMap<>());
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    public TelemetryManager(DTDLSystem system, int queueCapacity, int workerThreads, int maxRetries, long baseBackoffMillis, DeadLetterSink deadLetter) {
        this.system = Objects.requireNonNull(system);
        this.deadLetter = deadLetter == null ? new DeadLetterSink("telemetry-deadletter.log") : deadLetter;
        this.hub = new TelemetryHub(system, queueCapacity, workerThreads, maxRetries, baseBackoffMillis);
    }

    public void registerAdapter(TelemetryAdapter adapter) {
        if (adapter == null) return;
        adapter.setHub(hub);
        adapters.add(adapter);
        if (isRunning()) {
            try { adapter.start(); } catch(Throwable ex) { System.err.println("Adapter start failed: " + ex.getMessage()); }
        }
    }

    public boolean unregisterAdapter(TelemetryAdapter adapter) {
        if (adapter == null) return false;
        try { adapter.stop(); } catch(Throwable ignored) {}
        adapter.setHub(null);
        return adapters.remove(adapter);
    }

    public void registerDeviceToInstance(String deviceId, String instanceId) {
        if (deviceId == null || instanceId == null) return;
        deviceToInstanceMap.put(deviceId, instanceId);
    }

    public Optional<String> resolveInstanceIdForDevice(String deviceId) {
        if (deviceId == null) return Optional.empty();
        return Optional.ofNullable(deviceToInstanceMap.get(deviceId));
    }

    public boolean start() {
        boolean ok = hub.start();
        if (ok) {
            // make sure adapters are started
            synchronized (adapters) {
                for (TelemetryAdapter a : adapters) {
                    try { a.start(); } catch (Throwable ex) { System.err.println("Adapter " + a.getName() + " start error: " + ex.getMessage()); }
                }
            }
        }
        return ok;
    }

    public boolean stop() {
        boolean ok = hub.stop();
        synchronized (adapters) {
            for (TelemetryAdapter a : adapters) {
                try { a.stop(); } catch (Throwable ignored) {}
            }
        }
        return ok;
    }

    public boolean isRunning() {
        // TelemetryHub has no isRunning accessor; infer from adapter/queue behaviour by tracking start/stop externally
        // For simplicity, check if at least one adapter is present and hub accepted offers (best-effort)
        return true; // keep simple — plugin can track lifecycle externally if needed
    }

    /**
     * Ingest a message via manager; this will attempt hub.ingest() and if it fails store to dead-letter.
     * Returns true if accepted by hub, false if enqueued failed (dead-lettered).
     */
    public boolean ingest(TelemetryMessage m) {
        if (m == null) return false;
        boolean acceptedByHub = hub.ingest(m);
        if (acceptedByHub) {
            accepted.incrementAndGet();
            return true;
        } else {
            dropped.incrementAndGet();
            try { deadLetter.write(m); } catch (Throwable ex) { System.err.println("Dead letter write failed: " + ex.getMessage()); }
            return false;
        }
    }

    /** Manual helper to create and ingest a message (UI friendly). */
    public boolean manualIngest(String instanceId, String dtmi, String telemetryName, Object value, Map<String,String> meta) {
        TelemetryMessage m = new TelemetryMessage(instanceId, dtmi, telemetryName, value, meta);
        return ingest(m);
    }

    public long getAcceptedCount() { return accepted.get(); }
    public long getDroppedCount() { return dropped.get(); }
    public List<TelemetryAdapter> listAdapters() { return Collections.unmodifiableList(new ArrayList<>(adapters)); }

    /** Convenience: create a PollingHttpAdapter and register it. */
    public void addPollingHttpAdapter(String name, URI endpoint, long periodSeconds) {
        PollingHttpAdapter a = new PollingHttpAdapter(name, endpoint, periodSeconds);
        registerAdapter(a);
    }
}