package org.tzi.use.dtdl.runtime;

import org.tzi.use.dtdl.DTDLModel.Interface;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime instance of an Interface definition.
 * Adds telemetry storage and command handler registrations.
 */
public final class DTDLInstance {
    private final String id;
    private final String name;
    private final Interface iface;

    private final Map<String, Object> propertyValues = new ConcurrentHashMap<>();
    // telemetry: last value per telemetry name + history
    private final Map<String, Object> lastTelemetry = new ConcurrentHashMap<>();
    private final Map<String, List<Object>> telemetryHistory = new ConcurrentHashMap<>();

    private final Map<String, List<RelationshipLink>> relationships = new ConcurrentHashMap<>();
    private final Map<String, String> components = new ConcurrentHashMap<>();

    // command handlers: sync handler returning result; DTDLSystem will wrap in CompletableFuture
    // handler signature: Map<String,Object> args -> Object result (can be null)
    private final Map<String, Function<Map<String,Object>, Object>> commandHandlers = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private final Instant createdAt = Instant.now();

    /** Relationship link: a single target + optional per-link properties. */
    public static final class RelationshipLink {
        public final String targetInstanceId;
        public final Map<String, Object> properties;

        public RelationshipLink(String targetInstanceId, Map<String,Object> properties) {
            this.targetInstanceId = targetInstanceId;
            this.properties = properties == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        }

        @Override
        public String toString() {
            if (properties.isEmpty()) return targetInstanceId;
            return targetInstanceId + " " + properties.toString();
        }
    }

    public DTDLInstance(String id, String name, Interface iface) {
        this.id = id;
        this.name = name;
        this.iface = iface;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Interface getInterface() { return iface; }
    public boolean isRunning() { return running; }
    public Instant getCreatedAt() { return createdAt; }

    public void start() { this.running = true; }
    public void stop() { this.running = false; }

    /* properties (unchanged) */
    public Optional<Object> getProperty(String name) { return Optional.ofNullable(propertyValues.get(name)); }
    public void setProperty(String name, Object value) { propertyValues.put(name, value); }
    public Map<String,Object> getAllProperties() { return Collections.unmodifiableMap(propertyValues); }

    /* telemetry API */
    void setTelemetryInternal(String telemetryName, Object value) {
        if (telemetryName == null) return;
        lastTelemetry.put(telemetryName, value);
        telemetryHistory.computeIfAbsent(telemetryName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(value);
    }
    public Optional<Object> getLastTelemetry(String telemetryName) {
        return Optional.ofNullable(lastTelemetry.get(telemetryName));
    }
    public Map<String, Object> getAllLastTelemetry() { return Collections.unmodifiableMap(lastTelemetry); }
    public Map<String, List<Object>> getTelemetryHistory() { return Collections.unmodifiableMap(telemetryHistory); }


    public List<RelationshipLink> getRelationshipLinks(String relName) {
        List<RelationshipLink> lst = relationships.get(relName);
        if (lst == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(lst));
    }

    public void addRelationshipTarget(String relName, String targetInstanceId) {
        addRelationshipTarget(relName, targetInstanceId, null);
    }

    public void addRelationshipTarget(String relName, String targetInstanceId, Map<String,Object> properties) {
        if (relName == null || targetInstanceId == null) return;
        relationships.computeIfAbsent(relName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new RelationshipLink(targetInstanceId, properties));
    }

    public void removeRelationshipTarget(String relName, String targetInstanceId) {
        List<RelationshipLink> lst = relationships.get(relName);
        if (lst != null) {
            lst.removeIf(link -> targetInstanceId.equals(link.targetInstanceId));
        }
    }


    public Optional<String> getComponentInstanceId(String compName) { return Optional.ofNullable(components.get(compName)); }

    public void setComponentInstance(String compName, String instanceId) { components.put(compName, instanceId); }

    /* command handlers */
    /**
     * Register a command handler for a named command.
     * handler: Map args -> result (synchronous). DTDLSystem will run it asynchronously and return a CompletableFuture.
     */
    public void registerCommandHandler(String commandName, Function<Map<String,Object>, Object> handler) {
        if (commandName == null || handler == null) return;
        commandHandlers.put(commandName, handler);
    }

    public void unregisterCommandHandler(String commandName) {
        if (commandName == null) return;
        commandHandlers.remove(commandName);
    }

    /**
     * Internal: invoked by DTDLSystem to execute the command handler synchronously.
     * Returns Optional.empty() if handler not present.
     */
    Object executeCommandInternal(String commandName, Map<String,Object> args) {
        Function<Map<String,Object>, Object> h = commandHandlers.get(commandName);
        if (h == null) return null;
        return h.apply(args == null ? Collections.emptyMap() : args);
    }

    // ensure a telemetry key exists (we use Optional.empty() as "no value yet" sentinel)
    public void ensureTelemetryKey(String telemetryName) {
        if (telemetryName == null) return;
        // store Optional.empty() so map value is non-null but indicates "no emission yet"
        lastTelemetry.putIfAbsent(telemetryName, Optional.empty());
    }

    // ensure relationship key exists with an empty list (so print/GUI can show declared relationship)
    public void ensureRelationshipKey(String relName) {
        if (relName == null) return;
        relationships.computeIfAbsent(relName, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    @Override
    public String toString() {
        return "DTDLInstance{id=" + id + ", name=" + name + ", iface=" + (iface != null ? iface.getId() : "(null)") + "}";
    }


    public String printSummary() {
        return "DTDLInstance{id=" + id +
                ", name=" + name +
                ", iface=" + (iface != null ? iface.getId() : "null") +
                ", running=" + running +
                ", createdAt=" + createdAt +
                "}";
    }

    public String printProperties() {
        StringBuilder sb = new StringBuilder();
        sb.append("Properties:\n");
        for (var e : propertyValues.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    public String printTelemetry() {
        StringBuilder sb = new StringBuilder();
        sb.append("Telemetry (last values):\n");
        for (var e : lastTelemetry.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    public String printTelemetryHistory() {
        StringBuilder sb = new StringBuilder();
        sb.append("Telemetry history:\n");
        for (var e : telemetryHistory.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    public String printRelationships() {
        StringBuilder sb = new StringBuilder();
        sb.append("Relationships:\n");

        for (var e : relationships.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" ->\n");
            for (var link : e.getValue()) {
                sb.append("    targetInstanceId: ").append(link.targetInstanceId).append("\n");
                if (!link.properties.isEmpty()) {
                    sb.append("      properties:\n");
                    for (var p : link.properties.entrySet()) {
                        sb.append("        ").append(p.getKey())
                                .append(" = ").append(p.getValue()).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    public String printComponents() {
        StringBuilder sb = new StringBuilder();
        sb.append("Components:\n");
        for (var e : components.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    public String printCommands() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registered commands:\n");
        for (var k : commandHandlers.keySet()) {
            sb.append("  ").append(k).append("\n");
        }
        return sb.toString();
    }

    public String printAll() {
        StringBuilder sb = new StringBuilder();
        sb.append(printSummary()).append("\n");
        sb.append(printProperties());
        sb.append(printTelemetry());
        sb.append(printTelemetryHistory());
        sb.append(printRelationships());
        sb.append(printComponents());
        sb.append(printCommands());
        return sb.toString();
    }

    public void printToStdout() {
        System.out.println(printAll());
    }
}
