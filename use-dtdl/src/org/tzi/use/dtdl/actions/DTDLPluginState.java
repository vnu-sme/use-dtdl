package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.integration.operations.OperationCatalog;
import org.tzi.use.dtdl.integration.operations.OperationExecutionService;
import org.tzi.use.dtdl.integration.operations.UseOperationExecutor;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.telemetry.BindingRegistry;
import org.tzi.use.dtdl.telemetry.HttpPollingAdapter;
import org.tzi.use.dtdl.telemetry.TelemetryAdapter;
import org.tzi.use.dtdl.telemetry.TelemetryEngine;
import org.tzi.use.dtdl.telemetry.imports.AdapterImportSpec;
import org.tzi.use.dtdl.telemetry.imports.BindingImportSpec;
import org.tzi.use.dtdl.telemetry.imports.TelemetryImportReader;
import org.tzi.use.dtdl.telemetry.imports.TelemetryImportSpec;
import org.tzi.use.main.Session;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DTDLPluginState {
    private static DTDLModelRegistry registry;
    private static DTDLContext ctx;
    private static TelemetryEngine telemetryEngine;

    private static final ConcurrentHashMap<String, TelemetryAdapter> adapters = new ConcurrentHashMap<>();

    private static final OperationCatalog OPERATION_CATALOG = new OperationCatalog();
    private static OperationExecutionService operationService;
    private static Session operationServiceSession;

    private DTDLPluginState() {}

    public static DTDLModelRegistry registry() {
        if (registry == null) {
            registry = new DTDLModelRegistry();
        }
        return registry;
    }

    public static TelemetryEngine startTelemetryRuntime(Session session) {
        if (telemetryEngine == null) {
            telemetryEngine = new TelemetryEngine(session);
            telemetryEngine.start();
            System.err.println("[DTDLPluginState] TelemetryEngine started.");
        }
        return telemetryEngine;
    }

    public static TelemetryEngine telemetryEngine() {
        return telemetryEngine;
    }


    public static void registerAdapter(TelemetryAdapter adapter, Session session) {
        Objects.requireNonNull(adapter, "adapter");
        startTelemetryRuntime(session);

        TelemetryAdapter prev = adapters.putIfAbsent(adapter.id(), adapter);
        if (prev != null) {
            throw new IllegalStateException("Adapter already registered: " + adapter.id());
        }

        telemetryEngine.registerAdapter(adapter);
    }

    public static void startAllRegisteredAdapters() {
        if (telemetryEngine == null) {
            throw new IllegalStateException("Telemetry runtime not started");
        }
        for (String id : adapters.keySet()) {
            telemetryEngine.startAdapter(id);
        }
    }

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

    // IMPORT FLOW FOR TELEMETRY
    public static int registerTelemetryImport(File file, Session session) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(session, "session");

        startTelemetryRuntime(session);

        TelemetryImportSpec spec;
        try {
            spec = TelemetryImportReader.read(file);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read telemetry import file: " + ex.getMessage(), ex);
        }

        if (spec == null || spec.adapters == null || spec.adapters.isEmpty()) {
            return 0;
        }

        int registered = 0;

        for (AdapterImportSpec a : spec.adapters) {
            if (a == null) continue;
            if (a.url == null || a.url.isBlank()) {
                throw new IllegalArgumentException("Adapter url is required");
            }

            String adapterId = (a.id == null || a.id.isBlank())
                    ? "api-" + UUID.randomUUID().toString().substring(0, 8)
                    : a.id.trim();

            HttpPollingAdapter adapter = new HttpPollingAdapter(
                    adapterId,
                    a.url.trim(),
                    a.method,
                    a.intervalMs,
                    a.deviceId,
                    a.objectName
            );

            registerAdapter(adapter, session);

            for (BindingImportSpec b : a.bindings) {
                if (b == null) continue;

                String bindId = "bind-" + UUID.randomUUID().toString().substring(0, 8);
                String objectName = b.objectName != null ? b.objectName : a.objectName;

                telemetryEngine.registry().register(
                        bindId, new BindingRegistry.Binding(b.dtmi, b.telemetryName, adapter.id(), objectName, b.valuePath, b.fieldPaths));
            }

            registered++;
        }

        return registered;
    }

    public static Map<String, TelemetryAdapter> getRegisteredAdapters() {
        return new HashMap<>(adapters);
    }

    public static OperationCatalog operationCatalog() {
        return OPERATION_CATALOG;
    }

    public static synchronized OperationExecutionService operationService(Session session) {
        Objects.requireNonNull(session, "session");

        if (operationService == null || operationServiceSession != session) {
            operationService = new OperationExecutionService(session, new UseOperationExecutor(session, OPERATION_CATALOG));
            operationServiceSession = session;
        }

        return operationService;
    }
}