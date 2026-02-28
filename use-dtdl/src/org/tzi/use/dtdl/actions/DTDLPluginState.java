package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.telemetry.TelemetryAdapter;
import org.tzi.use.dtdl.telemetry.TelemetryEngine;
import org.tzi.use.main.Session;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DTDLPluginState {
    private static DTDLModelRegistry registry;
    private static DTDLContext ctx;
    private static TelemetryEngine telemetryEngine;

    // track adapters so they survive dialog close and can be detached later
    private static final ConcurrentHashMap<String, TelemetryAdapter> adapters = new ConcurrentHashMap<>();

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

    public static synchronized TelemetryEngine startTelemetryRuntime(Session session) {
        if (telemetryEngine == null) {
            telemetryEngine = new TelemetryEngine(session);
            // start bus and make engine ready to accept adapters
            telemetryEngine.start();
            System.err.println("[DTDLPluginState] TelemetryEngine started.");
        }
        return telemetryEngine;
    }

    public static synchronized TelemetryEngine telemetryEngine() {
        return telemetryEngine;
    }

    /**
     * Register an adapter in plugin state and attach it to the engine.
     */
    public static synchronized void registerAndAttachAdapter(TelemetryAdapter adapter, Session session) {
        Objects.requireNonNull(adapter, "adapter");
        startTelemetryRuntime(session);
        TelemetryAdapter prev = adapters.putIfAbsent(adapter.id(), adapter);
        if (prev != null) {
            throw new IllegalStateException("Adapter already registered: " + adapter.id());
        }
        try {
            telemetryEngine.attachAdapter(adapter);
        } catch (Throwable t) {
            // cleanup on failure
            adapters.remove(adapter.id());
            System.err.println("[DTDLPluginState] Failed to attach adapter " + adapter.id() + ": " + t.getMessage());
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    /**
     * Detach adapter from engine (if attached) and unregister it from plugin state.
     */
    public static synchronized void detachAndUnregisterAdapter(String adapterId) {
        if (adapterId == null) return;
        TelemetryAdapter removed = adapters.remove(adapterId);
        if (removed != null) {
            try {
                if (telemetryEngine != null) {
                    telemetryEngine.detachAdapter(adapterId);
                }
            } catch (Throwable t) {
                System.err.println("[DTDLPluginState] Error detaching adapter " + adapterId + ": " + t.getMessage());
            } finally {
                try {
                    removed.close();
                } catch (Throwable ignored) {}
            }
        }
    }

    public static synchronized Map<String, TelemetryAdapter> getRegisteredAdapters() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(adapters));
    }

    public static synchronized TelemetryAdapter getAdapter(String id) {
        return adapters.get(id);
    }

    public static synchronized void stopTelemetryRuntime() {
        if (telemetryEngine != null) {
            try {
                // close/unregister adapters first
                for (Map.Entry<String, TelemetryAdapter> en : adapters.entrySet()) {
                    try {
                        TelemetryAdapter a = en.getValue();
                        try { a.close(); } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                }
                adapters.clear();

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
