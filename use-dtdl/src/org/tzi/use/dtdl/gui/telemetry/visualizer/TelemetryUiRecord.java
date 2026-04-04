package org.tzi.use.dtdl.gui.telemetry.visualizer;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TelemetryUiRecord {
    public final Instant timestamp;
    public final String dtmi;
    public final String interfaceId;
    public final String adapterId;
    public final String objectName;
    public final String telemetryName;
    public final String status;
    public final String httpStatus;
    public final String rawValue;
    public final String normalizedValue;
    public final String binding;
    public final String message;
    public final Map<String, Object> meta;
    public final List<String> diagnostics;

    public TelemetryUiRecord(
            Instant timestamp,
            String dtmi,
            String interfaceId,
            String adapterId,
            String objectName,
            String telemetryName,
            String status,
            String httpStatus,
            String rawValue,
            String normalizedValue,
            String binding,
            String message,
            Map<String, Object> meta,
            List<String> diagnostics
    ) {
        this.timestamp = timestamp;
        this.dtmi = dtmi;
        this.interfaceId = interfaceId;
        this.adapterId = adapterId;
        this.objectName = objectName;
        this.telemetryName = telemetryName;
        this.status = status;
        this.httpStatus = httpStatus;
        this.rawValue = rawValue;
        this.normalizedValue = normalizedValue;
        this.binding = binding;
        this.message = message;
        this.meta = meta == null ? Collections.emptyMap() : Collections.unmodifiableMap(meta);
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    @Override
    public String toString() {
        return "TelemetryUiRecord{" +
                "timestamp=" + timestamp +
                ", adapterId='" + adapterId + '\'' +
                ", objectName='" + objectName + '\'' +
                ", telemetryName='" + telemetryName + '\'' +
                ", status='" + status + '\'' +
                ", httpStatus='" + httpStatus + '\'' +
                '}';
    }
}