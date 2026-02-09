package org.tzi.use.dtdl.telemetry;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

/**
 * High-level engine wiring adapters, event bus and processor together.
 * Updated to track attached adapters so they can be detached/closed.
 */
public final class TelemetryEngine implements AutoCloseable {
    private final BindingRegistry registry = new BindingRegistry();
    private final TelemetryProcessor processor = new TelemetryProcessor(registry);
    private final EventBus bus;

    // track attached adapters by id so we can detach/close them later
    private final ConcurrentHashMap<String, TelemetryAdapter> adapters = new ConcurrentHashMap<>();

    public TelemetryEngine() {
        this.bus = new EventBus(this::consume);
    }

    private void consume(TelemetryEvent ev) {
        try {
            // Always attempt to apply bindings that target this adapter. In the binding-driven design
            // adapters provide raw payloads; bindings map them to telemetry elements.
            int handled = 0;
            System.err.println("[TelemetryEngine] incoming raw event, scanning bindings for adapter=" + ev.source);

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
                } catch (Throwable t) {
                    System.err.println("[TelemetryEngine] processing failed for binding " + b + ": " + t.getMessage());
                    t.printStackTrace(System.err);
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
            } catch (Throwable t) {
                System.err.println("[TelemetryEngine] processing failed: " + t.getMessage());
                t.printStackTrace(System.err);
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
        Objects.requireNonNull(adapter);
        TelemetryAdapter prev = adapters.putIfAbsent(adapter.id(), adapter);
        if (prev != null) {
            throw new IllegalStateException("Adapter already attached: " + adapter.id());
        }

        System.err.println("[ENGINE] Attaching adapter: " + adapter.id());


        adapter.start(ev -> {
            // incoming adapter events are posted to bus
            try {
                bus.post(ev);
            } catch (Throwable t) {
                System.err.println("[TelemetryEngine] failed to post event: " + t.getMessage());
            }
        });
    }

    public void detachAdapter(String adapterId) {
        if (adapterId == null) return;
        TelemetryAdapter a = adapters.remove(adapterId);
        if (a != null) {
            try {
                a.close();
            } catch (Throwable t) {
                System.err.println("[TelemetryEngine] failed to close adapter " + adapterId + ": " + t.getMessage());
            }
        }
    }

    public TelemetryAdapter adapter(String id) {
        return adapters.get(id);
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
