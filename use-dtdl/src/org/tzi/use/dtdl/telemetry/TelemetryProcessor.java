package org.tzi.use.dtdl.telemetry;


import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.actions.DTDLPluginState;


import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;



/**
 * TelemetryProcessor: resolve DTMI -> interface, find telemetry definition and validate the value.
 * Produces TelemetryFact instances that are descriptive only (no state mutation).
 */
public final class TelemetryProcessor {
    private final BindingRegistry bindings;


    public TelemetryProcessor(BindingRegistry bindings) {
        this.bindings = bindings;
    }


    public TelemetryFact process(TelemetryEvent ev) {
        if (ev == null) throw new IllegalArgumentException("event required");
        DTDLModelRegistry reg = DTDLPluginState.registry();
        System.err.println("[PROCESSOR] Processing telemetry: dtmi=" + ev.dtmi +
                " name=" + ev.telemetryName +
                " raw=" + ev.rawValue);

        if (reg == null) {
            TelemetryFact f = new TelemetryFact(TelemetryFact.Status.UNKNOWN_INTERFACE, ev.dtmi, null, ev.telemetryName, null, ev.timestamp, ev.source, null, ev.meta);
            f.addDiag("DTDLModelRegistry not available (no registry loaded)");
            return f;
        }


        String dtmi = ev.dtmi;
        if (dtmi == null || dtmi.isEmpty()) {
            TelemetryFact f = new TelemetryFact(TelemetryFact.Status.UNKNOWN_INTERFACE, null, null, ev.telemetryName, null, ev.timestamp, ev.source, null, ev.meta);
            f.addDiag("Event missing dtmi");
            return f;
        }


        DTDLModel model = reg.getCanonicalModel();
        if (model == null) {
            TelemetryFact f = new TelemetryFact(TelemetryFact.Status.UNKNOWN_INTERFACE, dtmi, null, ev.telemetryName, null, ev.timestamp, ev.source, null, ev.meta);
            f.addDiag("Canonical DTDL model is empty");
            return f;
        }


        Interface iface = model.getInterface(dtmi);
        if (iface == null) {
            TelemetryFact f = new TelemetryFact(TelemetryFact.Status.UNKNOWN_INTERFACE, dtmi, null, ev.telemetryName, null, ev.timestamp, ev.source, null, ev.meta);
            f.addDiag("No interface found for dtmi: " + dtmi);
            return f;
        }

        // find matching content element by name (telemetry name)
        Object matchedContent = null;
        for (Object ce : iface.getContents()) {
            try {
                Method m = ce.getClass().getMethod("getName");
                Object name = m.invoke(ce);
                if (name != null && name.toString().equals(ev.telemetryName)) { matchedContent = ce; break; }
            } catch (NoSuchMethodException nm) {
                // ignore
            } catch (Throwable t) {
                // reflection problem
            }
        }

        if (matchedContent == null) {
            TelemetryFact f = new TelemetryFact(TelemetryFact.Status.UNBOUND, dtmi, iface.getId(), ev.telemetryName, null, ev.timestamp, ev.source, ev.objectName, ev.meta);
            f.addDiag("No content element found matching telemetry name: " + ev.telemetryName);
            return f;
        }

        // attempt to obtain schema via getSchema()
        Schema schema = null;
        try {
            Method getSchema = matchedContent.getClass().getMethod("getSchema");
            Object s = getSchema.invoke(matchedContent);
            if (s instanceof Schema) schema = (Schema) s;
        } catch (NoSuchMethodException ns) {
            // content element does not expose schema
        } catch (Throwable t) {
            // ignore
        }


        // validate / coerce raw value against schema (basic primitive support)
        Object normalized = null;
        boolean valid = true;
        if (schema instanceof PrimitiveType) {
            String tname = ((PrimitiveType) schema).getTypeName();
            var r = tryCoercePrimitive(ev.rawValue, tname);
            normalized = r.value;
            valid = r.ok;
        } else {
            // non-primitive: best-effort pass-through
            normalized = ev.rawValue;
        }


        if (!valid) {
            TelemetryFact f = new TelemetryFact(TelemetryFact.Status.INVALID, dtmi, iface.getId(), ev.telemetryName, null, ev.timestamp, ev.source, ev.objectName, ev.meta);
            f.addDiag("Failed to coerce value '" + ev.rawValue + "' to schema " + (schema == null ? "<unknown>" : schema.getClass().getSimpleName()));
            return f;
        }


        // find binding optionally
        Optional<BindingRegistry.Binding> b = bindings.find(dtmi, ev.telemetryName, ev.source);
        String matchedObject = b.map(bb -> bb.objectName).orElse(ev.objectName);


        TelemetryFact f = new TelemetryFact(TelemetryFact.Status.RESOLVED, dtmi, iface.getId(), ev.telemetryName, normalized, ev.timestamp, ev.source, matchedObject, ev.meta);
        f.addDiag("Matched interface and telemetry element");
        b.ifPresent(bb -> f.addDiag("Applied binding: " + bb.toString()));
        return f;
    }

    private static class CoerceResult {
        final boolean ok;
        final Object value;

        CoerceResult(boolean ok, Object value) {
            this.ok = ok;
            this.value = value;
        }
    }


    private static CoerceResult tryCoercePrimitive(Object raw, String tname) {
        if (raw == null) return new CoerceResult(true, null);
        if (tname == null) tname = "string";
        tname = tname.toLowerCase();
        try {
            if (tname.contains("int") || tname.equals("integer") || tname.equals("long")) {
                if (raw instanceof Number) return new CoerceResult(true, ((Number) raw).intValue());
                if (raw instanceof String) return new CoerceResult(true, Integer.parseInt(((String) raw).trim()));
                return new CoerceResult(false, null);
            }
            if (tname.contains("float") || tname.contains("double") || tname.equals("number") || tname.equals("real")) {
                if (raw instanceof Number) return new CoerceResult(true, ((Number) raw).doubleValue());
                if (raw instanceof String) return new CoerceResult(true, Double.parseDouble(((String) raw).trim()));
                return new CoerceResult(false, null);
            }
            if (tname.contains("bool")) {
                if (raw instanceof Boolean) return new CoerceResult(true, raw);
                if (raw instanceof String) {
                    String s = ((String) raw).trim();
                    if ("true".equalsIgnoreCase(s)) return new CoerceResult(true, Boolean.TRUE);
                    if ("false".equalsIgnoreCase(s)) return new CoerceResult(true, Boolean.FALSE);
                    return new CoerceResult(false, null);
                }
                return new CoerceResult(false, null);
            }
            // default to string
            return new CoerceResult(true, String.valueOf(raw));
        } catch (Exception ex) {
            return new CoerceResult(false, null);
        }
    }
}