package org.tzi.use.dtdl.runtime;

import com.google.common.eventbus.EventBus;
import org.tzi.use.dtdl.DTDLModel.Command.Command;
import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Property.Property;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;
import org.tzi.use.dtdl.DTDLModel.Telemetry.Telemetry;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manager for runtime instances. Similar role to USE's MSystem:
 * - holds instances
 * - lifecycle operations (create/start/stop/delete)
 * - event bus for UI and listeners
 */
public final class DTDLSystem {
    private final DTDLModelRegistry modelRegistry;
    private final DTDLContext ctx;
    private final EventBus eventBus = new EventBus("DTDL System");
    private final ConcurrentHashMap<String, DTDLInstance> instances = new ConcurrentHashMap<>();

    // creation/deletion listeners (optional convenience)
    private final List<Consumer<DTDLInstance>> onCreate = Collections.synchronizedList(new ArrayList<>());
    private final List<Consumer<DTDLInstance>> onDelete = Collections.synchronizedList(new ArrayList<>());

    private final ExecutorService commandExecutor = Executors.newCachedThreadPool();

    public DTDLSystem(DTDLModelRegistry registry, DTDLContext ctx) {
        this.modelRegistry = registry;
        this.ctx = ctx;
    }

    public EventBus getEventBus() { return eventBus; }


    /**
     * Validated variant — returns created instance or throws IllegalArgumentException
     * when initial property validation fails.
     */
    public DTDLInstance createInstanceValidated(Interface iface, String name, Map<String,Object> initialProperties) {
        if (iface == null) throw new IllegalArgumentException("Interface is null");
        // Validate & coerce properties (throws IllegalArgumentException on error)
        Map<String, Object> coercedProps = validateAndCoerceProperties(iface, initialProperties);

        // create id and instance object
        String id = UUID.randomUUID().toString();
        String displayName = name != null ? name : (iface.getId() + "#" + id.substring(0,8));
        DTDLInstance inst = new DTDLInstance(id, displayName, iface);

        // set properties
        coercedProps.forEach(inst::setProperty);

        // --- initialize telemetry keys, relationships and default command handlers ---
        for (ContentElement c : iface.getContents()) {
            // telemetry
            if (c instanceof Telemetry t) {
                inst.ensureTelemetryKey(t.getName());
            }

            // relationships (ensure relationship slot exists so GUI/prints show it)
            if (c.getClass().getSimpleName().equals("Relationship")) {
                try {
                    var rel = (Relationship) c;
                    inst.ensureRelationshipKey(rel.getName());
                } catch (ClassCastException ignored) {
                    // ignore if not available — defensive
                }
            }

            // commands: register a simple default handler so the command appears as "available"
            if (c.getClass().getSimpleName().equals("Command")) {
                try {
                    var cmd = (Command) c;
                    String cmdName = cmd.getName();
                    // default handler returns a small status map so callers get predictable response
                    inst.registerCommandHandler(cmdName, args -> {
                        // optionally: you could attempt to validate args here against cmd.getRequest().getSchema()
                        return Collections.unmodifiableMap(Map.of("status", "not-implemented", "command", cmdName));
                    });
                } catch (ClassCastException ignored) {
                    // defensive
                }
            }
        }

        // register instance
        instances.put(id, inst);
        fireCreate(inst);

        return inst;
    }

    /**
     * Backwards-compatible createInstance: returns null on validation failure.
     */
    public DTDLInstance createInstance(Interface iface, String name, Map<String,Object> initialProperties) {
        try {
            return createInstanceValidated(iface, name, initialProperties);
        } catch (IllegalArgumentException ex) {
            // keep behavior compatible: return null (caller may show generic failure message)
            // Optionally log the error to stdout/stderr for developer feedback:
            System.err.println("createInstance failed: " + ex.getMessage());
            return null;
        }
    }


    /**
     * Validate and coerce initial properties according to the Interface schema.
     * Returns a new Map containing coerced values or throws IllegalArgumentException on validation failure.
     */
    private Map<String, Object> validateAndCoerceProperties(Interface iface, Map<String, Object> initialProperties) {
        if (iface == null) throw new IllegalArgumentException("Interface is null");

        // Build property definition map for quick lookup
        Map<String, Property> defs = new LinkedHashMap<>();
        for (ContentElement c : iface.getContents()) {
            if (c instanceof Property p) {
                defs.put(p.getName(), p);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        if (initialProperties != null) {
            for (Map.Entry<String, Object> en : initialProperties.entrySet()) {
                String propName = en.getKey();
                Object raw = en.getValue();

                Property def = defs.get(propName);
                Schema schema = def != null ? def.getSchema() : null;

                Object coerced = coerceToSchemaRecursive(schema, raw);
                if (raw != null && coerced == null) {
                    errors.add("Property '" + propName + "' has invalid value: " + String.valueOf(raw));
                } else if (coerced != null) {
                    out.put(propName, coerced);
                } else {
                    // coerced == null && raw == null -> skip (no initial value)
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        return out;
    }

    /**
     * Attempt to coerce 'value' to schema. Returns coerced object or null if failure.
     * Accepts:
     *  - primitive coercions (string -> int/double/boolean)
     *  - enums (string name)
     *  - object schemas (Map<String,Object> -> coerced map recursively)
     *  - map schemas (Map -> coerced map)
     *  - array schemas (List -> coerced list)
     *
     * Note: we do NOT attempt to parse JSON strings here; caller should pass typed objects.
     */
    @SuppressWarnings("unchecked")
    private Object coerceToSchemaRecursive(org.tzi.use.dtdl.DTDLModel.Schema.Schema schema, Object value) {
        if (schema == null) {
            // no schema defined: accept the raw value as-is
            return value;
        }
        if (value == null) return null;

        // Primitive
        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType pt) {
            String t = pt.getTypeName();
            // if already correct type, accept (Number -> integer/long/double conversion below)
            if (t.equals("boolean")) {
                if (value instanceof Boolean b) return b;
                if (value instanceof String vs) {
                    if ("true".equalsIgnoreCase(vs)) return Boolean.TRUE;
                    if ("false".equalsIgnoreCase(vs)) return Boolean.FALSE;
                    return null;
                }
                return null;
            }
            if (t.equals("integer")) {
                if (value instanceof Integer i) return i;
                if (value instanceof Number n) return n.intValue();
                if (value instanceof String s) {
                    try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return null; }
                }
                return null;
            }
            if (t.equals("long")) {
                if (value instanceof Long l) return l;
                if (value instanceof Number n) return n.longValue();
                if (value instanceof String s) {
                    try { return Long.parseLong(s.trim()); } catch (Exception ex) { return null; }
                }
                return null;
            }
            if (t.equals("double") || t.equals("float")) {
                if (value instanceof Double d) return d;
                if (value instanceof Number n) return n.doubleValue();
                if (value instanceof String s) {
                    try { return Double.parseDouble(s.trim()); } catch (Exception ex) { return null; }
                }
                return null;
            }
            // string / default
            if (value instanceof String s) return s;
            return String.valueOf(value);
        }

        // Enum
        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum enm) {
            // accept string that matches EnumValue.name()
            if (value instanceof String vs) {
                for (org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue ev : enm.getValues()) {
                    if (ev != null && ev.getName().equals(vs)) return vs;
                    // also accept underlying literal (enumValue) if present
                    if (ev != null && ev.getValue() != null) {
                        Object lit = ev.getValue().raw();
                        if (lit != null && lit.toString().equals(vs)) return vs;
                    }
                }
            }
            return null;
        }

        // Object
        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object objSchema) {
            if (!(value instanceof Map<?, ?>)) {
                return null;
            }
            Map<String, Object> inMap = (Map<String,Object>) value;
            Map<String, Object> outMap = new LinkedHashMap<>();
            for (org.tzi.use.dtdl.DTDLModel.Schema.Object.Field f : objSchema.getFields()) {
                String fname = f.getName();
                org.tzi.use.dtdl.DTDLModel.Schema.Schema fSchema = f.getSchema();
                Object rawField = inMap.get(fname);
                Object coercedField = coerceToSchemaRecursive(fSchema, rawField);
                if (rawField != null && coercedField == null) {
                    // any field invalid -> fail entire object
                    return null;
                }
                if (coercedField != null) outMap.put(fname, coercedField);
            }
            return outMap;
        }

        // Map
        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map mapSchema) {
            if (!(value instanceof Map<?, ?>)) return null;
            Map<?,?> in = (Map<?,?>) value;
            Map<Object,Object> outMap = new LinkedHashMap<>();
            org.tzi.use.dtdl.DTDLModel.Schema.Map.MapKey mk = mapSchema.getMapKey();
            org.tzi.use.dtdl.DTDLModel.Schema.Map.MapValue mv = mapSchema.getMapValue();
            org.tzi.use.dtdl.DTDLModel.Schema.Schema keySchema = mk != null ? mk.getSchema() : null;
            org.tzi.use.dtdl.DTDLModel.Schema.Schema valueSchema = mv != null ? mv.getSchema() : null;
            for (Map.Entry<?,?> ent : in.entrySet()) {
                Object rawK = ent.getKey();
                Object rawV = ent.getValue();
                Object kCoerced = coerceToSchemaRecursive(keySchema, rawK);
                Object vCoerced = coerceToSchemaRecursive(valueSchema, rawV);
                if (rawK != null && kCoerced == null) return null;
                if (rawV != null && vCoerced == null) return null;
                outMap.put(kCoerced != null ? kCoerced : rawK, vCoerced != null ? vCoerced : rawV);
            }
            return outMap;
        }

        // Array
        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Array.Array arrSchema) {
            if (!(value instanceof List<?>)) return null;
            List<?> inList = (List<?>) value;
            List<Object> outList = new ArrayList<>();
            org.tzi.use.dtdl.DTDLModel.Schema.Schema elemSchema = arrSchema.getElementSchema();
            for (Object elem : inList) {
                Object coerced = coerceToSchemaRecursive(elemSchema, elem);
                if (elem != null && coerced == null) return null;
                outList.add(coerced != null ? coerced : elem);
            }
            return outList;
        }

        // fallback: unknown schema subtype -> accept raw value
        return value;
    }



    private void fireCreate(DTDLInstance inst) {
        // post event to EventBus and call listeners
        eventBus.post(new DTDLInstanceCreatedEvent(inst));
        synchronized (onCreate) {
            onCreate.forEach(l -> { try { l.accept(inst); } catch(Exception ignored) {} });
        }
    }

    private void fireDelete(DTDLInstance inst) {
        eventBus.post(new DTDLInstanceDeletedEvent(inst));
        synchronized (onDelete) {
            onDelete.forEach(l -> { try { l.accept(inst); } catch(Exception ignored) {} });
        }
    }

    public void addOnCreateListener(Consumer<DTDLInstance> l) { onCreate.add(l); }
    public void addOnDeleteListener(Consumer<DTDLInstance> l) { onDelete.add(l); }

    public Optional<DTDLInstance> getInstance(String id) { return Optional.ofNullable(instances.get(id)); }
    public List<DTDLInstance> listInstances() { return new ArrayList<>(instances.values()); }

    public boolean startInstance(String id) {
        DTDLInstance i = instances.get(id);
        if (i == null) return false;
        i.start();
        eventBus.post(new DTDLInstanceStartedEvent(i));
        return true;
    }
    public boolean stopInstance(String id) {
        DTDLInstance i = instances.get(id);
        if (i == null) return false;
        i.stop();
        eventBus.post(new DTDLInstanceStoppedEvent(i));
        return true;
    }

    public boolean deleteInstance(String id) {
        DTDLInstance inst = instances.remove(id);
        if (inst == null) return false;
        fireDelete(inst);
        return true;
    }

    public boolean linkRelationship(String sourceInstanceId, String relName, String targetInstanceId) {
        return linkRelationship(sourceInstanceId, relName, targetInstanceId, null);
    }

    /**
     * Link relationship with optional per-link properties. Validates multiplicity and relationship property schemas.
     */
    public boolean linkRelationship(String sourceInstanceId, String relName, String targetInstanceId, Map<String,Object> linkProperties) {
        DTDLInstance src = instances.get(sourceInstanceId);
        DTDLInstance tgt = instances.get(targetInstanceId);
        if (src == null || tgt == null) return false;

        Interface srcIface = src.getInterface();
        Relationship relDef = null;
        if (srcIface != null) {
            for (ContentElement ce : srcIface.getContents()) {
                if (ce instanceof Relationship r) {
                    if (r.getName() != null && r.getName().equals(relName)) {
                        relDef = r;
                        break;
                    }
                }
            }
        }


        if (relDef != null) {
            Interface targetRef = relDef.getTarget();
            if (targetRef != null) {
                String expectedDtmi = targetRef.getId();

                Interface actual = tgt.getInterface();
                if (actual == null || !expectedDtmi.equals(actual.getId())) {
                    return false;
                }
            }

            Integer max = relDef.getMaxMultiplicity();
            List<?> existing = src.getRelationshipLinks(relName);
            int current = existing == null ? 0 : existing.size();

            if (max != null && max >= 1 && current >= max) {
                return false;
            }

            Map<String,Object> coercedProps = new LinkedHashMap<>();
            if (linkProperties != null && !linkProperties.isEmpty()) {
                for (Property propDef : relDef.getProperties()) {
                    String pname = propDef.getName();
                    if (linkProperties.containsKey(pname)) {
                        Object raw = linkProperties.get(pname);
                        Object coerced = coerceToSchemaRecursive(propDef.getSchema(), raw);
                        if (raw != null && coerced == null) {
                            System.out.println("FALSE HERE COERCED");
                            return false;
                        }
                        if (coerced != null) coercedProps.put(pname, coerced);
                    }
                }
                for (String provided : linkProperties.keySet()) {
                    boolean known = relDef.getProperties().stream().anyMatch(pd -> {
                        String n = pd.getName();
                        return n != null && n.equals(provided);
                    });
                    if (!known) {
                        System.out.println("FALSE HERE KNOWN");
                        return false;
                    }
                }
            }

            src.addRelationshipTarget(relName, targetInstanceId, coercedProps);
            eventBus.post(new DTDLRelationshipLinkedEvent(src, relName, tgt));
            return true;
        } else {
            src.addRelationshipTarget(relName, targetInstanceId, linkProperties);
            eventBus.post(new DTDLRelationshipLinkedEvent(src, relName, tgt));
            return true;
        }
    }



    public boolean setComponent(String instanceId, String compName, String componentInstanceId) {
        DTDLInstance inst = instances.get(instanceId);
        DTDLInstance comp = instances.get(componentInstanceId);
        if (inst == null || comp == null) return false;
        inst.setComponentInstance(compName, componentInstanceId);
        eventBus.post(new DTDLComponentSetEvent(inst, compName, comp));
        return true;
    }

    /**
     * Emit telemetry from a given instance. Stores telemetry on instance and posts event.
     */
    public boolean emitTelemetry(String instanceId, String telemetryName, Object value) {
        if (instanceId == null || telemetryName == null) return false;
        DTDLInstance inst = instances.get(instanceId);
        if (inst == null) return false;

        // update instance telemetry storage
        inst.setTelemetryInternal(telemetryName, value);

        // post event for UI / listeners
        eventBus.post(new DTDLTelemetryEvent(inst, telemetryName, value));
        return true;
    }

    /**
     * Invoke a command on an instance.
     * Returns a CompletableFuture that completes with the handler result or exceptionally if not found/error.
     */
    public CompletableFuture<Object> invokeCommand(String instanceId, String commandName, Map<String,Object> args) {
        DTDLInstance inst = instances.get(instanceId);
        if (inst == null) {
            CompletableFuture<Object> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("Instance not found: " + instanceId));
            return f;
        }

        // Post invoked event (fire before executing)
        eventBus.post(new DTDLCommandInvokedEvent(inst, commandName, args));

        // run handler asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                Object result = inst.executeCommandInternal(commandName, args);
                // post response event (success)
                eventBus.post(new DTDLCommandResponseEvent(inst, commandName, result, null));
                return result;
            } catch (Throwable ex) {
                // post response event (failure)
                eventBus.post(new DTDLCommandResponseEvent(inst, commandName, null, ex));
                throw ex;
            }
        }, commandExecutor);
    }


    /**
     * Public entry point for telemetry ingestion from external sources.
     * Validates/coerces telemetry value against declared telemetry schema (if available) and stores it.
     * Returns true on success, false on validation/route failure.
     */
    public boolean acceptTelemetry(String instanceId, String telemetryName, Object rawValue) {
        if (instanceId == null || telemetryName == null) return false;
        DTDLInstance inst = instances.get(instanceId);
        if (inst == null) return false;

        // find telemetry definition in interface (if available)
        org.tzi.use.dtdl.DTDLModel.Interface iface = inst.getInterface();
        org.tzi.use.dtdl.DTDLModel.Schema.Schema telSchema = null;
        if (iface != null) {
            for (ContentElement ce : iface.getContents()) {
                if (ce instanceof Telemetry t && telemetryName.equals(t.getName())) {
                    telSchema = t.getSchema();
                    break;
                }
            }
        }

        // attempt to coerce/validate if schema exists
        Object coerced = telSchema != null ? coerceToSchemaRecursive(telSchema, rawValue) : rawValue;
        if (rawValue != null && coerced == null) {
            // validation failed
            return false;
        }

        // store telemetry and post event
        inst.setTelemetryInternal(telemetryName, coerced);
        eventBus.post(new DTDLTelemetryEvent(inst, telemetryName, coerced));
        return true;
    }

    /**
     * Find instances whose interface id equals the given dtmi.
     * Useful for routing when telemetry provides a DTMI instead of instance id.
     */
    public List<DTDLInstance> findInstancesByInterfaceId(String dtmi) {
        if (dtmi == null) return Collections.emptyList();
        List<DTDLInstance> out = new ArrayList<>();
        for (DTDLInstance i : instances.values()) {
            var iface = i.getInterface();
            if (iface != null && dtmi.equals(iface.getId())) out.add(i);
        }
        return out;
    }
}
