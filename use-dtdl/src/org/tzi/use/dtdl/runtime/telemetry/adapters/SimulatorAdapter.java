package org.tzi.use.dtdl.runtime.telemetry.adapters;

import org.tzi.use.dtdl.runtime.telemetry.TelemetryAdapter;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryHub;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryMessage;

import java.util.Map;
import java.util.concurrent.*;

public class SimulatorAdapter implements TelemetryAdapter {
    private TelemetryHub hub;
    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "sim-adapter"));
    private final String name;
    private final long periodSeconds;
    private final String dtmi;
    private final String telemetryName;
    private final String instanceId; // optional

    public SimulatorAdapter(String name, String dtmi, String telemetryName, String instanceId, long periodSeconds) {
        this.name = name;
        this.dtmi = dtmi;
        this.telemetryName = telemetryName;
        this.instanceId = instanceId;
        this.periodSeconds = Math.max(1L, periodSeconds);
    }

    @Override public void setHub(TelemetryHub hub) { this.hub = hub; }
    @Override public String getName() { return name; }

    @Override
    public void start() {
        sched.scheduleWithFixedDelay(this::emit, 0, periodSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        sched.shutdownNow();
    }

    private void emit() {
        try {
            Object val = Math.round((20 + Math.random()*10) * 10.0) / 10.0;
            TelemetryMessage m = new TelemetryMessage(instanceId, dtmi, telemetryName, val, Map.of("source", name));
            if (hub != null) hub.ingest(m);
        } catch (Throwable ex) {
            System.err.println("SimulatorAdapter error: " + ex.getMessage());
        }
    }
}