package org.tzi.use.dtdl.telemetry;


import java.io.Closeable;
import java.util.function.Consumer;


/**
 * Adapter interface for incoming telemetry sources. Adapters should parse raw input
 * and emit TelemetryEvent instances to the provided handler.
 */
public interface TelemetryAdapter extends Closeable {
    /**
     * Start the adapter. Provide a Consumer to receive TelemetryEvent objects.
     */
    void start(Consumer<TelemetryEvent> handler);


    /**
     * Stop the adapter. Close any resources.
     */
    @Override
    void close();


    /**
     * Human identifier for adapter instance.
     */
    String id();
}