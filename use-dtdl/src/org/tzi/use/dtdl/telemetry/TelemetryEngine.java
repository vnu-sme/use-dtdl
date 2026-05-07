package org.tzi.use.dtdl.telemetry;

import org.tzi.use.dtdl.gui.telemetry.visualizer.TelemetryUiListener;
import org.tzi.use.dtdl.gui.telemetry.visualizer.TelemetryUiRecord;
import org.tzi.use.dtdl.util.telemetry.RobustWindowGate;
import org.tzi.use.main.Session;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.tzi.use.dtdl.util.Utils.*;

/**
 * High-level engine wiring adapters, event bus and processor together.
 * Updated to track attached adapters so they can be detached/closed.
 */
public final class TelemetryEngine implements AutoCloseable {
    private final BindingRegistry registry = new BindingRegistry();
    private final TelemetryProcessor processor = new TelemetryProcessor(registry);
    private final EventBus bus;
    private final Session session;
    private final List<TelemetryEventListener> listeners = new CopyOnWriteArrayList<>();


    // track attached adapters by id so we can detach/close them later
    private final ConcurrentHashMap<String, TelemetryAdapter> adapters = new ConcurrentHashMap<>();
    private final Set<String> warmupConsumedAdapters = ConcurrentHashMap.newKeySet();

    private static final int MAX_UI_HISTORY = 500;
    private final Deque<TelemetryUiRecord> uiHistory = new ArrayDeque<>();
    private final CopyOnWriteArrayList<TelemetryUiListener> uiListeners = new CopyOnWriteArrayList<>();

    // detect anomaly
    private final RobustWindowGate anomalyGate = new RobustWindowGate(
            envInt("TELEM_MAX_SAMPLES", 9),
            envInt("TELEM_MIN_SAMPLES", 5),
            envDouble("TELEM_Z_THRESHOLD", 3.5),
            envInt("TELEM_CONFIRM_COUNT", 2),
            envLong("TELEM_MAX_AGE_MS", 120_000L)
    );


    public TelemetryEngine(Session session) {
        this.session = session;
        this.bus = new EventBus(this::consume);
    }

    private void consume(TelemetryEvent ev) {
        try {
            // Always attempt to apply bindings that target this adapter. In the binding-driven design
            // adapters provide raw payloads; bindings map them to telemetry elements.
            int handled = 0;
            System.err.println("[TelemetryEngine] incoming raw event, scanning bindings for adapter=" + ev.source);

            boolean warmup = ev != null && ev.source != null && warmupConsumedAdapters.add(ev.source);

            for (BindingRegistry.Binding b : registry.bindingsForAdapter(ev.source)) {
                // adapter must match if binding specifies one
                if (b.adapterId != null && ev.source != null && !b.adapterId.equals(ev.source)) continue;
                // if binding specifies a dtmi, require it to match event dtmi (if event dtmi present)
                if (b.dtmi != null && ev.dtmi != null && !b.dtmi.equals(ev.dtmi)) continue;

                System.err.println("[TelemetryEngine] dispatching event to processor for binding: " + b);
                try {
                    TelemetryFact fact = processor.process(ev, b);
                    System.err.println("[TelemetryEngine] fact=" + fact);
                    for (String d : fact.diagnostics) System.err.println(" diag: " + d);

                    // APPLY and CHECK after processing
                    if (!applyWithRobustGate(ev, b, fact, warmup)) {
                        handled++;
                        continue;
                    }

                } catch (Throwable t) {
                    System.err.println("[TelemetryEngine] processing failed for binding " + b + ": " + t.getMessage());
                    t.printStackTrace(System.err);

                    publishUiRecord(buildUiRecord(ev, b, null, false, "ERROR", t.getMessage()));
                }

                handled++;
            }

            if (handled > 0) {
                System.err.println("[TelemetryEngine] processed " + handled + " binding(s) for adapter=" + ev.source);
                return;
            }

            // no binding matched -> Default single-binding processing
            System.err.println("[TelemetryEngine] no matching bindings found for adapter=" + ev.source + ", using default processing");
            try {
                TelemetryFact fact = processor.process(ev);
                System.err.println("[TelemetryEngine] fact=" + fact);
                for (String d : fact.diagnostics) System.err.println(" diag: " + d);

                applyWithRobustGate(ev, null, fact, warmup);
            } catch (Throwable t) {
                System.err.println("[TelemetryEngine] processing failed: " + t.getMessage());
                t.printStackTrace(System.err);

                publishUiRecord(buildUiRecord(ev, null, null, false, "ERROR", t.getMessage()));
            }
        } catch (Throwable t) {
            System.err.println("[TelemetryEngine] consume error: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }


    public void start() {
        bus.start();
    }

    public void stop() {
        bus.stop();
    }

    public BindingRegistry registry() {
        return registry;
    }

    public EventBus bus() {
        return bus;
    }

    public void attachAdapter(TelemetryAdapter adapter) {
        registerAdapter(adapter);
        startAdapter(adapter.id());
    }

    public void registerAdapter(TelemetryAdapter adapter) {
        Objects.requireNonNull(adapter);
        TelemetryAdapter prev = adapters.putIfAbsent(adapter.id(), adapter);
        if (prev != null) {
            throw new IllegalStateException("Adapter already registered: " + adapter.id());
        }
    }

    public void startAdapter(String adapterId) {
        TelemetryAdapter adapter = adapters.get(adapterId);
        if (adapter == null) {
            throw new IllegalStateException("Adapter not registered: " + adapterId);
        }

        warmupConsumedAdapters.remove(adapterId);

        System.err.println("[ENGINE] Starting adapter: " + adapter.id());

        adapter.start(ev -> {
            try {
                bus.post(ev);
            } catch (Throwable t) {
                System.err.println("[TelemetryEngine] failed to post event: " + t.getMessage());
            }
        });

        publishUiRecord(new TelemetryUiRecord(
                Instant.now(), null, null, resolveAdapterName(adapter.id()),
                null, null, "RUNNING", null, null, null, null,
                "Adapter started", null, List.of()
        ));
    }

    public void detachAdapter(String adapterId) {
        if (adapterId == null) return;
        TelemetryAdapter a = adapters.remove(adapterId);

        warmupConsumedAdapters.remove(adapterId);

        if (a != null) {
            try {
                a.close();
                publishUiRecord(new TelemetryUiRecord(Instant.now(), null, null, resolveAdapterName(adapterId), null,
                        null, "STOPPED", null, null, null, null,
                        "Adapter detached", null, List.of()));
            } catch (Throwable t) {
                System.err.println("[TelemetryEngine] failed to close adapter " + adapterId + ": " + t.getMessage());
            }
        }
    }

    public TelemetryAdapter adapter(String id) {
        return adapters.get(id);
    }

    private String resolveAdapterName(String adapterId) {
        if (adapterId == null) {
            return "";
        }

        TelemetryAdapter adapter = adapters.get(adapterId);
        if (adapter == null) {
            return adapterId;
        }

        return getAdapterDeviceId(adapter);
    }

    public String getAdapterDeviceId(TelemetryAdapter adapter) {
        if (adapter == null) {
            return "";
        }

        if (adapter instanceof HttpPollingAdapter http) {
            if (http.deviceId() != null && !http.deviceId().isBlank()) {
                return http.deviceId();
            }
        }

        return adapter.id();
    }

    public void addListener(TelemetryEventListener l) {
        listeners.add(l);
    }

    public void removeListener(TelemetryEventListener l) {
        listeners.remove(l);
    }

    void fireViolation(String adapterId, String message) {
        for (TelemetryEventListener l : listeners) {
            try {
                l.onTelemetryViolation(adapterId, message);
            } catch (Throwable ignored) {}
        }
    }

    private boolean applyWithRobustGate(TelemetryEvent ev, BindingRegistry.Binding b, TelemetryFact fact, boolean warmup) {
        RobustWindowGate.Decision decision = anomalyGate.inspect(fact);

        if (!decision.allow) {
            System.out.println("[TELEM][FILTERED] " + "obj=" + fact.matchedObjectName + ", telem=" + fact.telemetryName + ", reason=" + decision.message);
            if (decision.message != null) {
                fact.addDiag(decision.message);
            }

            publishUiRecord(buildUiRecord(ev, b, fact, false, "FILTERED", decision.message));
            return false;
        }

        if (decision.message != null) {
            fact.addDiag(decision.message);
        }


        boolean violated = false;

        if (warmup) {
            new TelemetryApplier(session).applyAndCheck(fact, false);
        } else {
            violated = new TelemetryApplier(session).applyAndCheck(fact, true);
        }

        String status;
        if (warmup) {
            status = "WARMUP";
        } else if (decision.confirmed) {
            status = violated ? "CONFIRMED_ANOMALY_VIOLATION" : "CONFIRMED_ANOMALY";
        } else {
            status = violated
                    ? "VIOLATION"
                    : (fact.status == TelemetryFact.Status.INVALID ? "INVALID" : "APPLIED");
        }

        publishUiRecord(buildUiRecord(ev, b, fact, violated, status,
                fact.diagnostics.isEmpty() ? null : fact.diagnostics.get(fact.diagnostics.size() - 1)));

        System.out.println("[TELEM][APPLY] " + "obj=" + fact.matchedObjectName + ", telem=" + fact.telemetryName + ", value=" + fact.normalizedValue
                + ", violated=" + violated + ", status=" + status);

        if (violated) {
            String details = String.join("\n", fact.diagnostics);
            String adapterId = b != null && b.adapterId != null ? b.adapterId : ev.source;
            try {
                TelemetryApplier.handleViolationAndStop(adapterId, details);
            } catch (RuntimeException ex) {
                System.err.println("[TelemetryEngine] violation triggered stop: " + ex.getMessage());
            }
        }
        return true;
    }



    public void addUiListener(TelemetryUiListener listener) {
        if (listener != null) {
            uiListeners.addIfAbsent(listener);
        }
    }

    public void removeUiListener(TelemetryUiListener listener) {
        if (listener != null) {
            uiListeners.remove(listener);
        }
    }

    public List<TelemetryUiRecord> history() {
        synchronized (uiHistory) {
            return new ArrayList<>(uiHistory);
        }
    }

    private void publishUiRecord(TelemetryUiRecord record) {
        if (record == null) {
            return;
        }

        synchronized (uiHistory) {
            uiHistory.addLast(record);
            while (uiHistory.size() > MAX_UI_HISTORY) {
                uiHistory.removeFirst();
            }
        }

        for (TelemetryUiListener listener : uiListeners) {
            try {
                listener.onTelemetryRecord(record);
            } catch (Throwable t) {
                System.err.println("[TelemetryEngine] UI listener error: " + t.getMessage());
            }
        }
    }

    private TelemetryUiRecord buildUiRecord(TelemetryEvent ev, BindingRegistry.Binding binding, TelemetryFact fact,
            boolean violated, String status, String message) {
        String httpStatus = null;
        if (ev != null && ev.meta != null && ev.meta.get("httpStatus") != null) {
            httpStatus = String.valueOf(ev.meta.get("httpStatus"));
        }

        String rawValue = ev == null || ev.rawValue == null ? null : String.valueOf(ev.rawValue);
        String normalizedValue = fact == null || fact.normalizedValue == null ? null : String.valueOf(fact.normalizedValue);

        return new TelemetryUiRecord(
                Instant.now(),
                ev == null ? null : ev.dtmi,
                fact == null ? null : fact.interfaceId,
                resolveAdapterName(ev == null ? null : ev.source),
                fact == null ? (ev == null ? null : ev.objectName) : fact.matchedObjectName,
                fact == null ? null : fact.telemetryName,
                status,
                httpStatus,
                rawValue,
                normalizedValue,
                binding == null ? null : binding.toString(),
                message,
                ev == null ? null : ev.meta,
                fact == null ? List.of() : new ArrayList<>(fact.diagnostics)
        );
    }

    @Override
    public void close() {
        try {
            for (Map.Entry<String, TelemetryAdapter> en : adapters.entrySet()) {
                try {
                    en.getValue().close();
                } catch (Throwable ignore) {
                }
            }
            adapters.clear();
        } finally {
            stop();
        }
    }
}
