package org.tzi.use.dtdl.telemetry;

public interface TelemetryEventListener {
    void onTelemetryViolation(String adapterId, String message);
}