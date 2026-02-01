package org.tzi.use.dtdl.telemetry;


import java.io.Closeable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;


/**
 * Simple in-process adapter used to inject telemetry for testing/simulation.
 * Call post(...) to deliver a synthetic event to the system.
 */
public class SimulatorAdapter implements TelemetryAdapter {
    private final String id;
    private final AtomicReference<Consumer<TelemetryEvent>> handler = new AtomicReference<>();


    public SimulatorAdapter(String id) {
        this.id = Objects.requireNonNull(id);
    }


    @Override
    public void start(Consumer<TelemetryEvent> handler) {
        this.handler.set(handler);
    }


    public void post(String dtmi, String deviceId, String objectName, String telemetryName,
                     Object rawValue, Instant ts, Map<String,Object> meta) {
        Consumer<TelemetryEvent> h = handler.get();
        if (h == null) throw new IllegalStateException("SimulatorAdapter not started with handler");
        TelemetryEvent ev = new TelemetryEvent(dtmi, deviceId, objectName, telemetryName, rawValue, ts == null ? Instant.now() : ts, id, meta);
        h.accept(ev);
    }


    @Override
    public void close() { handler.set(null); }


    @Override
    public String id() { return id; }
}