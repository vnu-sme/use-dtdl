package org.tzi.use.dtdl.runtime.telemetry;

import org.tzi.use.dtdl.runtime.DTDLSystem;
import org.tzi.use.dtdl.runtime.DTDLInstance;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class TelemetryMessage {
    public final String instanceId;      // optional, prefer if available
    public final String dtmi;            // optional: interface id to resolve instance(s)
    public final String telemetryName;   // required
    public final Object value;           // raw value (String/Number/Map/List etc)
    public final Map<String,String> meta; // optional metadata (source, sensor id, etc)
    public final Instant receivedAt;
    public final long sequence;

    private static final AtomicLong SEQ = new AtomicLong();

    public TelemetryMessage(String instanceId, String dtmi, String telemetryName, Object value, Map<String,String> meta) {
        this.instanceId = instanceId;
        this.dtmi = dtmi;
        this.telemetryName = Objects.requireNonNull(telemetryName);
        this.value = value;
        this.meta = meta;
        this.receivedAt = Instant.now();
        this.sequence = SEQ.incrementAndGet();
    }
}
