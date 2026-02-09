package org.tzi.use.dtdl.telemetry;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public final class TelemetryFact {
    public enum Status { RESOLVED, UNBOUND, UNKNOWN_INTERFACE, INVALID }


    public final Status status;
    public final String dtmi;
    public final String interfaceId;
    public final String telemetryName;
    public final Object normalizedValue;
    public final Instant timestamp;
    public final String source;
    public final String matchedObjectName; // optional
    public final Map<String, Object> meta;
    public final List<String> diagnostics = new ArrayList<>();


    public TelemetryFact(Status status, String dtmi, String interfaceId, String telemetryName, Object normalizedValue,
                         Instant timestamp, String source, String matchedObjectName, Map<String, Object> meta) {
        this.status = status;
        this.dtmi = dtmi;
        this.interfaceId = interfaceId;
        this.telemetryName = telemetryName;
        this.normalizedValue = normalizedValue;
        this.timestamp = timestamp;
        this.source = source;
        this.matchedObjectName = matchedObjectName;
        this.meta = meta;
    }


    public void addDiag(String s) { diagnostics.add(s); }


    @Override
    public String toString() {
        return "TelemetryFact{" +
                "status=" + status + ", interfaceId='" + interfaceId + '\'' + ", telemetryName='" + telemetryName + '\'' +
                ", normalizedValue=" + normalizedValue + ", matchedObjectName='" + matchedObjectName + '\'' +
                ", timestamp=" + timestamp + '}';
    }
}

