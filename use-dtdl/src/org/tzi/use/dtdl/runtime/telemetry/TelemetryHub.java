package org.tzi.use.dtdl.runtime.telemetry;

import org.tzi.use.dtdl.runtime.DTDLSystem;
import org.tzi.use.dtdl.runtime.DTDLInstance;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small, resilient ingestion hub:
 *  - bounded queue for backpressure
 *  - configurable worker pool
 *  - retries with simple backoff on transient failures
 *  - adapters register themselves with the hub
 */
public final class TelemetryHub {
    private final DTDLSystem system;
    private final BlockingQueue<TelemetryMessage> queue;
    private final List<TelemetryAdapter> adapters = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int maxRetries;
    private final long baseBackoffMillis;

    public TelemetryHub(DTDLSystem system, int queueCapacity, int workerThreads, int maxRetries, long baseBackoffMillis) {
        this.system = system;
        this.queue = new LinkedBlockingQueue<>(Math.max(16, queueCapacity));
        this.workers = Executors.newFixedThreadPool(Math.max(1, workerThreads), r -> {
            Thread t = new Thread(r, "telemetry-worker");
            t.setDaemon(true);
            return t;
        });
        this.maxRetries = Math.max(0, maxRetries);
        this.baseBackoffMillis = Math.max(10L, baseBackoffMillis);
    }

    public boolean start() {
        if (!running.compareAndSet(false, true)) return false;
        // start worker loops
        for (int i = 0; i < ((ThreadPoolExecutor) workers).getMaximumPoolSize(); i++) {
            workers.submit(this::workerLoop);
        }
        // start adapters
        synchronized (adapters) {
            for (TelemetryAdapter a : adapters) {
                try { a.start(); } catch (Throwable ignored) {}
            }
        }
        return true;
    }

    public boolean stop() {
        if (!running.compareAndSet(true, false)) return false;
        // stop adapters
        synchronized (adapters) {
            for (TelemetryAdapter a : adapters) {
                try { a.stop(); } catch (Throwable ignored) {}
            }
        }
        workers.shutdownNow();
        return true;
    }

    public void registerAdapter(TelemetryAdapter adapter) {
        if (adapter == null) return;
        adapter.setHub(this);
        adapters.add(adapter);
        if (running.get()) {
            try { adapter.start(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Non-blocking ingest; returns true when accepted, false if queue full.
     * Caller (adapter) should decide whether to drop, buffer to disk, or block/retry.
     */
    public boolean ingest(TelemetryMessage msg) {
        if (!running.get()) return false;
        return queue.offer(msg);
    }

    private void workerLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                TelemetryMessage m = queue.poll(500, TimeUnit.MILLISECONDS);
                if (m == null) continue;
                processWithRetries(m);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                // swallow unexpected exceptions to keep worker alive
                System.err.println("Telemetry worker unexpected error: " + ex.getMessage());
            }
        }
    }

    private void processWithRetries(TelemetryMessage m) {
        int attempt = 0;
        while (true) {
            boolean ok = processSingle(m);
            if (ok) return;
            if (attempt >= maxRetries) {
                System.err.println("TelemetryHub: dropping message after retries seq=" + m.sequence + " telemetry=" + m.telemetryName);
                return;
            }
            attempt++;
            long backoff = baseBackoffMillis * (1L << Math.min(10, attempt-1));
            try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
    }

    /**
     * Core processing:
     *  - resolve instance (prefer instanceId, else try dtmi -> any matching instance)
     *  - call system.acceptTelemetry(...)
     */
    private boolean processSingle(TelemetryMessage m) {
        try {
            // prefer explicit instanceId
            if (m.instanceId != null) {
                boolean r = system.acceptTelemetry(m.instanceId, m.telemetryName, m.value);
                if (!r) {
                    System.err.println("TelemetryHub: system rejected telemetry for instanceId=" + m.instanceId + " telemetry=" + m.telemetryName);
                }
                return r;
            }

            // fallback: if dtmi provided, try to find a matching instance
            if (m.dtmi != null) {
                var candidates = system.findInstancesByInterfaceId(m.dtmi);
                if (!candidates.isEmpty()) {
                    // simple choice: push to the first candidate (you can implement better routing)
                    DTDLInstance inst = candidates.get(0);
                    boolean r = system.acceptTelemetry(inst.getId(), m.telemetryName, m.value);
                    if (!r) {
                        System.err.println("TelemetryHub: system rejected telemetry for resolved instance " + inst.getId());
                    }
                    return r;
                } else {
                    System.err.println("TelemetryHub: no instances found for dtmi=" + m.dtmi);
                    return false;
                }
            }

            // unable to route
            System.err.println("TelemetryHub: telemetry message unrouteable (no instanceId/dtmi) seq=" + m.sequence);
            return false;
        } catch (Throwable ex) {
            System.err.println("TelemetryHub.processSingle error: " + ex.getMessage());
            return false;
        }
    }
}