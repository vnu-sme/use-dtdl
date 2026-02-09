package org.tzi.use.dtdl.telemetry;


import java.time.Instant;
import java.util.Map;


public final class TelemetryEvent {
    public final String dtmi; // optional, usually null
    public final String deviceId; // optional
    public final String objectName; // optional (explicit target USE object)
    public final Object rawValue; // raw payload value (String, Number, Map, List...)
    public final Instant timestamp; // event timestamp (may be null)
    public final String source; // adapter id or source description
    public final Map<String, Object> meta; // additional metadata

    public TelemetryEvent(String dtmi, String deviceId, String objectName,
                          Object rawValue, Instant timestamp, String source, Map<String, Object> meta) {
        this.dtmi = dtmi;
        this.deviceId = deviceId;
        this.objectName = objectName;
        this.rawValue = rawValue;
        this.timestamp = timestamp;
        this.source = source;
        this.meta = meta;
    }

    @Override
    public String toString() {
        return "TelemetryEvent{" +
                "dtmi='" + dtmi + '\'' + ", deviceId='" + deviceId + '\'' + ", objectName='" + objectName + '\'' +
                ", rawValue=" + rawValue + ", timestamp=" + timestamp +
                ", source='" + source + '\'' + '}';
    }
}