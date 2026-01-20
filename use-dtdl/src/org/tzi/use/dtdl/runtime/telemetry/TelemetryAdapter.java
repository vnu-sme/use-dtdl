package org.tzi.use.dtdl.runtime.telemetry;

public interface TelemetryAdapter {
    // Called after registration. Adapter should push TelemetryMessage into hub.ingest(...)
    void start();
    void stop();
    void setHub(TelemetryHub hub);
    String getName();
}