package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.runtime.DTDLSystem;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryManager;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryHub;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;

import java.util.Objects;

public final class DTDLPluginState {
    private static DTDLModelRegistry registry;
    private static DTDLContext ctx;
    private static DTDLSystem system;
    private static TelemetryManager telemetryManager;

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

    public static synchronized TelemetryManager telemetryManager() {
        return telemetryManager;
    }

    public static synchronized void setTelemetryManager(TelemetryManager m) {
        telemetryManager = m;
    }
}
