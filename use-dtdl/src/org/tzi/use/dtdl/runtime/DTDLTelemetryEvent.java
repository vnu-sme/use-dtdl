package org.tzi.use.dtdl.runtime;

public final class DTDLTelemetryEvent {
    public final DTDLInstance instance;
    public final String telemetryName;
    public final Object value;

    public DTDLTelemetryEvent(DTDLInstance i, String n, Object v){
        instance=i;
        telemetryName=n;
        value=v;
    }
}

