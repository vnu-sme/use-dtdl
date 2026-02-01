package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.runtime.DTDLSystem;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.telemetry.TelemetryEngine;

import java.util.Objects;

public final class DTDLPluginState {
    private static DTDLModelRegistry registry;
    private static DTDLContext ctx;
    private static DTDLSystem system;
    private static TelemetryEngine telemetryEngine;

    private DTDLPluginState() {}

    public static synchronized DTDLModelRegistry registry() {
        if (registry == null) {
            registry = new DTDLModelRegistry();
        }
        return registry;
    }

    public static synchronized DTDLContext context() {
        if (ctx == null) {
            ctx = new DTDLContext(registry());
        }
        return ctx;
    }

    public static synchronized DTDLSystem system() {
        if (system == null) {
            system = new DTDLSystem(registry(), context());
        }
        return system;
    }

    public static synchronized TelemetryEngine startTelemetryRuntime() {
        System.err.println("[ENGINE] Telemetry runtime started");

        if (telemetryEngine == null) {
            telemetryEngine = new TelemetryEngine();
            // start bus and make engine ready to accept adapters
            telemetryEngine.start();
            System.err.println("[DTDLPluginState] TelemetryEngine started.");
        }
        return telemetryEngine;
    }

    public static synchronized TelemetryEngine telemetryEngine() {
        return startTelemetryRuntime();
    }

    public static synchronized void stopTelemetryRuntime() {
        if (telemetryEngine != null) {
            try {
                telemetryEngine.close();
            } catch (Throwable t) {
                System.err.println("[DTDLPluginState] Error closing telemetry engine: " + t.getMessage());
            } finally {
                telemetryEngine = null;
                System.err.println("[DTDLPluginState] TelemetryEngine stopped.");
            }
        }
    }
}
