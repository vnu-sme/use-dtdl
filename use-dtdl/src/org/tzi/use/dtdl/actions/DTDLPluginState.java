package org.tzi.use.dtdl.actions;

import com.google.common.eventbus.Subscribe;
import org.tzi.use.dtdl.integration.operations.OperationCatalog;
import org.tzi.use.dtdl.integration.operations.OperationExecutionRegistry;
import org.tzi.use.dtdl.integration.operations.OperationExecutionService;
import org.tzi.use.dtdl.integration.operations.UseOperationExecutor;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.telemetry.TelemetryAdapter;
import org.tzi.use.dtdl.telemetry.TelemetryEngine;
import org.tzi.use.main.Session;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.events.Event;

import org.tzi.use.main.ChangeEvent;
import org.tzi.use.main.ChangeListener;
import org.tzi.use.uml.sys.events.StatementExecutedEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DTDLPluginState {
    private static DTDLModelRegistry registry;
    private static DTDLContext ctx;
    private static TelemetryEngine telemetryEngine;

    private static final ConcurrentHashMap<String, TelemetryAdapter> adapters = new ConcurrentHashMap<>();

    private static final OperationCatalog OPERATION_CATALOG = new OperationCatalog();
    private static final OperationExecutionRegistry OPERATION_RULES = OperationExecutionRegistry.loadDefault();
    private static OperationExecutionService operationService;

    private static Session boundSession;
    private static MSystem boundSystem;
    private static boolean sessionChangeListenerInstalled = false;
    private static volatile boolean pendingEvaluation = false;

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
        bindSession(session);
        if (telemetryEngine == null) {
            telemetryEngine = new TelemetryEngine(session);
            telemetryEngine.start();
            System.err.println("[DTDLPluginState] TelemetryEngine started.");
        }
        return telemetryEngine;
    }

    public static synchronized TelemetryEngine telemetryEngine() {
        return telemetryEngine;
    }

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
            adapters.remove(adapter.id());
            System.err.println("[DTDLPluginState] Failed to attach adapter " + adapter.id() + ": " + t.getMessage());
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
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

    public static synchronized Map<String, TelemetryAdapter> getRegisteredAdapters() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(adapters));
    }

    public static synchronized TelemetryAdapter getAdapter(String id) {
        return adapters.get(id);
    }

    public static synchronized void stopTelemetryRuntime() {
        if (boundSystem != null) {
            try {
                boundSystem.getEventBus().unregister(SYSTEM_EVENT_BRIDGE);
            } catch (Throwable ignored) {}
            boundSystem = null;
        }

        if (boundSession != null && sessionChangeListenerInstalled) {
            try {
                boundSession.removeChangeListener(SESSION_CHANGE_LISTENER);
            } catch (Throwable ignored) {}
        }

        sessionChangeListenerInstalled = false;

        if (telemetryEngine != null) {
            try {
                for (Map.Entry<String, TelemetryAdapter> en : adapters.entrySet()) {
                    try {
                        en.getValue().close();
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

    public static OperationCatalog operationCatalog() {
        return OPERATION_CATALOG;
    }

    private static final ChangeListener SESSION_CHANGE_LISTENER = new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
            attachSystemEventBridge();
        }
    };

    private static final Object SYSTEM_EVENT_BRIDGE = new Object() {
            @Subscribe
            public void onAnySystemEvent(Event event) {
                if (event instanceof StatementExecutedEvent) {
                    pendingEvaluation = true;
                }
            }
    };

    public static synchronized void bindSession(Session session) {
        if (session == null) {
            return;
        }

        if (boundSession != session) {
            if (boundSession != null && sessionChangeListenerInstalled) {
                try {
                    boundSession.removeChangeListener(SESSION_CHANGE_LISTENER);
                } catch (Throwable ignored) {}
            }
            boundSession = session;
            sessionChangeListenerInstalled = false;
        }

        if (!sessionChangeListenerInstalled) {
            session.addChangeListener(SESSION_CHANGE_LISTENER);
            sessionChangeListenerInstalled = true;
        }

        if (operationService == null) {
            operationService = new OperationExecutionService(
                    session,
                    OPERATION_RULES,
                    new UseOperationExecutor(session, OPERATION_CATALOG)
            );

            startRuleScheduler(); // ✅ ADD THIS
        }

        attachSystemEventBridge();
    }

    private static void startRuleScheduler() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50); // small delay

                    if (pendingEvaluation && operationService != null) {
                        pendingEvaluation = false;
                        operationService.evaluateAll(); // ✅ SAFE HERE
                    }

                } catch (Throwable ignored) {}
            }
        }, "op-rule-scheduler").start();
    }

    public static synchronized OperationExecutionService operationService(Session session) {
        bindSession(session);
        return operationService;
    }

    public static synchronized OperationExecutionService operationService() {
        return operationService;
    }

    private static synchronized void attachSystemEventBridge() {
        if (boundSession == null || !boundSession.hasSystem()) {
            return;
        }

        MSystem current = boundSession.system();
        if (current == boundSystem) {
            return;
        }

        if (boundSystem != null) {
            try {
                boundSystem.getEventBus().unregister(SYSTEM_EVENT_BRIDGE);
            } catch (Throwable ignored) {}
        }

        boundSystem = current;

        try {
            boundSystem.getEventBus().register(SYSTEM_EVENT_BRIDGE);
            System.err.println("[DTDLPluginState] System event bridge attached.");
        } catch (Throwable t) {
            System.err.println("[DTDLPluginState] Failed to attach system event bridge: " + t.getMessage());
        }
    }
}