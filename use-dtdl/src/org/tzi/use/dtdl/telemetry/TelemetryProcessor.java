package org.tzi.use.dtdl.telemetry;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;
import org.tzi.use.dtdl.DTDLModel.Schema.Object.Field;
import org.tzi.use.dtdl.DTDLModel.Schema.Map.MapKey;
import org.tzi.use.dtdl.DTDLModel.Schema.Map.MapValue;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.actions.DTDLPluginState;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tzi.use.dtdl.util.JacksonPath;

import java.util.*;
import java.util.Optional;

public final class TelemetryProcessor {
    private final BindingRegistry bindings;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public TelemetryProcessor(BindingRegistry bindings) {
        this.bindings = bindings;
    }

    public TelemetryFact process(TelemetryEvent ev) {
        return doProcess(bindings.find(ev.dtmi, null, ev.source), ev);
    }

    public TelemetryFact process(TelemetryEvent ev, BindingRegistry.Binding binding) {
        return doProcess(Optional.ofNullable(binding), ev);
    }

    private TelemetryFact doProcess(Optional<BindingRegistry.Binding> bindingOpt, TelemetryEvent ev) {
        if (ev == null) throw new IllegalArgumentException("event required");

        DTDLModelRegistry reg = DTDLPluginState.registry();
        if (reg == null || reg.getCanonicalModel() == null) {
            TelemetryFact f = new TelemetryFact(
                    TelemetryFact.Status.UNKNOWN_INTERFACE,
                    ev.dtmi, null, null,
                    null, ev.timestamp, ev.source,
                    ev.objectName, ev.meta
            );
            f.addDiag("DTDL model not available");
            return f;
        }

        DTDLModel model = reg.getCanonicalModel();

        // Determine telemetry name to use for lookups: prefer binding.telemetryName if present
        String telemetryNameForLookup = null;
        if (bindingOpt.isPresent()) {
            BindingRegistry.Binding bb = bindingOpt.get();
            if (bb.telemetryName != null && !bb.telemetryName.isBlank()) telemetryNameForLookup = bb.telemetryName;
        }

        // Try to locate an interface (DTMI). If event provided dtmi use it; otherwise try to find an interface that contains the telemetry
        String resolvedDtmi = ev.dtmi;
        Interface iface = null;

        if (resolvedDtmi != null && !resolvedDtmi.isBlank()) {
            iface = model.getInterface(resolvedDtmi);
            if (iface == null) {
                System.err.println("[PROCESSOR] Provided dtmi not found in canonical model: " + resolvedDtmi);
            }
        }

        if (iface == null && telemetryNameForLookup != null) {
            for (Interface cand : model.getInterfaces().values()) {
                for (ContentElement ce : cand.getContents()) {
                    if (telemetryNameForLookup.equals(ce.getName())) {
                        iface = cand;
                        resolvedDtmi = cand.getId();
                        break;
                    }
                }
                if (iface != null) break;
            }
        }

        // final attempt: if binding explicitly specifies a dtmi, use it
        if (iface == null && bindingOpt.isPresent()) {
            BindingRegistry.Binding bb = bindingOpt.get();
            if (bb.dtmi != null && !bb.dtmi.isBlank()) {
                iface = model.getInterface(bb.dtmi);
                if (iface != null) {
                    resolvedDtmi = bb.dtmi;
                    System.err.println("[PROCESSOR] Using binding.dtmi=" + bb.dtmi + " to resolve interface");
                }
            }
        }

        // If we still have no interface and there's no binding, we can't proceed
        if (iface == null && bindingOpt.isEmpty()) {
            TelemetryFact f = new TelemetryFact(TelemetryFact.Status.UNKNOWN_INTERFACE, ev.dtmi, null, telemetryNameForLookup, null, ev.timestamp, ev.source, ev.objectName, ev.meta);
            f.addDiag("No interface found and no binding available (dtmi=" + ev.dtmi + ", tele=" + telemetryNameForLookup + ")");
            return f;
        }

        // find matching content element by name (telemetry name) if possible
        ContentElement matchedContent = null;
        if (telemetryNameForLookup != null) {
            for (ContentElement ce : iface.getContents()) {
                if (telemetryNameForLookup.equals(ce.getName())) {
                    matchedContent = ce;
                    break;
                }
            }
        }

        // attempt to obtain schema via getSchema() if we have matched content
        Schema schema = null;
        if (matchedContent instanceof org.tzi.use.dtdl.DTDLModel.Telemetry.Telemetry t) {
            schema = t.getSchema();
        } else if (matchedContent instanceof org.tzi.use.dtdl.DTDLModel.Property.Property p) {
            schema = p.getSchema();
        } else if (matchedContent instanceof org.tzi.use.dtdl.DTDLModel.Command.Command c) {
            if (c.getRequest() != null) schema = c.getRequest().getSchema();
        }

        // Processing/coercion: either use binding if present (valuePath or fieldPaths), otherwise default behaviour
        Object normalized = null;
        boolean valid = true;

        try {
            if (bindingOpt.isPresent()) {
                BindingRegistry.Binding b = bindingOpt.get();
                if (b.valuePath != null) {
                    Object extracted = JacksonPath.extract(
                            ev.rawValue instanceof String ? (String) ev.rawValue : String.valueOf(ev.rawValue),
                            b.valuePath
                    );
                    System.err.println("[PROCESSOR] Binding valuePath extracted: " + extracted + " for path=" + b.valuePath);
                    if (schema != null && schema instanceof PrimitiveType) {
                        var r = tryCoercePrimitive(extracted, ((PrimitiveType) schema).getTypeName());
                        valid = r.ok;
                        if (!valid) {
                            normalized = null;
                        } else {
                            // wrapIfNeeded will only wrap non-primitive schemas, but it's safe to call here
                            normalized = wrapIfNeeded(r.value, schema, telemetryNameForLookup, b);
                        }
                    } else {
                        var r = coerceAgainstSchema(schema, extracted);
                        valid = r.ok;
                        if (!valid) {
                            normalized = null;
                        } else {
                            // For non-primitive schemas (Object/Map/Array/DataType) wrap the coerced result
                            // under the telemetry name so TelemetryApplier writes the whole attribute.
                            normalized = wrapIfNeeded(r.value, schema, telemetryNameForLookup, b);
                        }
                    }
                } else if (b.fieldPaths != null) {
                    System.err.println("[PROCESSOR] Using fieldPaths binding for telemetry='" + b.telemetryName + "'");
                    try {
                        Object parsedRaw = ev.rawValue;
                        if (parsedRaw instanceof String) {
                            String s = ((String) parsedRaw).trim();
                            if (s.startsWith("{") || s.startsWith("[")) {
                                parsedRaw = MAPPER.readValue(s, Object.class);
                            }
                        }

                        System.err.println("[PROCESSOR] Parsed raw value type = " + (parsedRaw == null ? "null" : parsedRaw.getClass().getName()));

                        if (parsedRaw instanceof List) {
                            List<?> rawList = (List<?>) parsedRaw;
                            List<Object> assembledList = new ArrayList<>(rawList.size());

                            for (Object elem : rawList) {
                                Map<String, Object> assembledElem = new LinkedHashMap<>();
                                String elemJson;
                                try {
                                    elemJson = (elem instanceof String) ? (String) elem : MAPPER.writeValueAsString(elem);
                                } catch (Exception ex) {
                                    elemJson = String.valueOf(elem);
                                }

                                for (Map.Entry<String, String> en : b.fieldPaths.entrySet()) {
                                    String fname = en.getKey();
                                    String path = en.getValue();
                                    if (path == null || path.isBlank()) {
                                        assembledElem.put(fname, null);
                                    } else {
                                        Object extracted = JacksonPath.extract(elemJson, path);
                                        assembledElem.put(fname, extracted);
                                        System.err.println("[PROCESSOR] Binding field (array element) extracted: field=" + fname + " path=" + path + " value=" + extracted);
                                    }
                                }
                                assembledList.add(assembledElem);
                            }

                            var r = coerceAgainstSchema(schema, assembledList);
                            valid = r.ok;
                            if (!valid) {
                                normalized = null;
                            } else {
                                normalized = wrapIfNeeded(r.value, schema, telemetryNameForLookup, b);
                            }
                        } else {
                            Map<String, Object> assembled = new LinkedHashMap<>();
                            String baseJson;
                            try {
                                baseJson = (parsedRaw instanceof String) ? (String) parsedRaw : MAPPER.writeValueAsString(parsedRaw);
                            } catch (Exception ex) {
                                baseJson = String.valueOf(parsedRaw);
                            }

                            for (Map.Entry<String, String> en : b.fieldPaths.entrySet()) {
                                String fname = en.getKey();
                                String path = en.getValue();
                                if (path == null || path.isBlank()) {
                                    assembled.put(fname, null);
                                } else {
                                    Object extracted = JacksonPath.extract(baseJson, path);
                                    assembled.put(fname, extracted);
                                    System.err.println("[PROCESSOR] Binding field extracted: field=" + fname + " path=" + path + " value=" + extracted);
                                }
                            }

                            var r = coerceAgainstSchema(schema, assembled);
                            valid = r.ok;
                            if (!valid) {
                                normalized = null;
                            } else {
                                // wrap the object under telemetry name so TelemetryApplier will write the full DataType attribute
                                normalized = wrapIfNeeded(r.value, schema, telemetryNameForLookup, b);
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("[PROCESSOR] binding fieldPaths extraction failed: " + ex.getMessage());
                        ex.printStackTrace(System.err);
                        valid = false;
                        normalized = null;
                    }
                } else {
                    // binding present but no specific paths -> fall back to default behaviour (coerce ev.rawValue)
                    if (schema instanceof PrimitiveType) {
                        var r = tryCoercePrimitive(ev.rawValue, ((PrimitiveType) schema).getTypeName());
                        normalized = r.value;
                        valid = r.ok;
                    } else {
                        var r = coerceAgainstSchema(schema, ev.rawValue);
                        normalized = r.value;
                        valid = r.ok;
                    }
                }
            } else {
                // no binding: original behavior (coerce using schema if present)
                if (schema instanceof PrimitiveType) {
                    var r = tryCoercePrimitive(ev.rawValue, ((PrimitiveType) schema).getTypeName());
                    normalized = r.value;
                    valid = r.ok;
                } else {
                    var r = coerceAgainstSchema(schema, ev.rawValue);
                    normalized = r.value;
                    valid = r.ok;
                }
            }
        } catch (Throwable t) {
            System.err.println("[PROCESSOR] extraction/coercion failed: " + t.getMessage());
            t.printStackTrace(System.err);
            valid = false;
            normalized = null;
        }

        if (!valid) {
            TelemetryFact f = new TelemetryFact(TelemetryFact.Status.INVALID, resolvedDtmi, iface == null ? null : iface.getId(), telemetryNameForLookup, null, ev.timestamp, ev.source, ev.objectName, ev.meta);
            f.addDiag("Failed to coerce value '" + ev.rawValue + "' to schema " + (schema == null ? "<unknown>" : schema.getClass().getSimpleName()));
            bindingOpt.ifPresent(bb -> f.addDiag("Binding present: " + bb.toString()));
            return f;
        }

        String matchedObject = bindingOpt.map(bb -> bb.objectName).orElse(ev.objectName);

        TelemetryFact f = new TelemetryFact(TelemetryFact.Status.RESOLVED, resolvedDtmi, iface == null ? null : iface.getId(), telemetryNameForLookup, normalized, ev.timestamp, ev.source, matchedObject, ev.meta);
        f.addDiag("Matched interface and telemetry element (or applied binding)");
        bindingOpt.ifPresent(bb -> f.addDiag("Applied binding: " + bb.toString()));
        return f;
    }


    private record CoerceResult(boolean ok, Object value) {}

    private static CoerceResult tryCoercePrimitive(Object raw, String tname) {
        if (raw == null) return new CoerceResult(true, null);
        if (tname == null) tname = "string";
        tname = tname.toLowerCase();
        try {
            boolean intCond = tname.contains("int") || tname.equals("integer") || tname.equals("long");
            boolean floatCond = tname.contains("float") || tname.contains("double") || tname.equals("number") || tname.equals("real");
            if (raw instanceof String) {
                String s = ((String) raw).trim();
                if (intCond) return new CoerceResult(true, Integer.parseInt(s));
                if (floatCond) return new CoerceResult(true, Double.parseDouble(s));
                if (tname.contains("bool")) {
                    if ("true".equalsIgnoreCase(s)) return new CoerceResult(true, Boolean.TRUE);
                    if ("false".equalsIgnoreCase(s)) return new CoerceResult(true, Boolean.FALSE);
                    return new CoerceResult(false, null);
                }
                return new CoerceResult(true, s);
            }
            if (intCond) {
                if (raw instanceof Number) return new CoerceResult(true, ((Number) raw).intValue());
                return new CoerceResult(false, null);
            }
            if (floatCond) {
                if (raw instanceof Number) return new CoerceResult(true, ((Number) raw).doubleValue());
                return new CoerceResult(false, null);
            }
            if (tname.contains("bool")) {
                if (raw instanceof Boolean) return new CoerceResult(true, raw);
                return new CoerceResult(false, null);
            }
            return new CoerceResult(true, String.valueOf(raw));
        } catch (Exception ex) {
            return new CoerceResult(false, null);
        }
    }

    private static CoerceResult coerceAgainstSchema(Schema schema, Object raw) {
        if (schema == null) return new CoerceResult(true, raw);
        Object parsed = raw;
        try {
            if (raw instanceof String) {
                String s = ((String) raw).trim();
                if (s.startsWith("{") || s.startsWith("[")) {
                    parsed = MAPPER.readValue(s, Object.class);
                }
            }
        } catch (Exception e) {
            return new CoerceResult(false, null);
        }

        if (schema instanceof PrimitiveType) {
            return tryCoercePrimitive(parsed, ((PrimitiveType) schema).getTypeName());
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object objSchema) {
            Map<String, Object> out = new LinkedHashMap<>();
            Map<?,?> srcMap = parsed instanceof Map ? (Map<?,?>) parsed : null;
            for (Field f : objSchema.getFields()) {
                String name = f.getName();
                Schema fs = f.getSchema();
                Object v = (srcMap != null) ? srcMap.get(name) : null;
                CoerceResult cr = coerceAgainstSchema(fs, v);
                if (!cr.ok) return new CoerceResult(false, null);
                out.put(name, cr.value);
            }
            return new CoerceResult(true, out);
        }

        if (schema instanceof Array arrSchema) {
            Schema elemSchema = arrSchema.getElementSchema();
            List<?> srcList = parsed instanceof List ? (List<?>) parsed : null;
            if (srcList == null) return new CoerceResult(false, null);
            List<Object> out = new ArrayList<>(srcList.size());
            for (Object e : srcList) {
                CoerceResult cr = coerceAgainstSchema(elemSchema, e);
                if (!cr.ok) return new CoerceResult(false, null);
                out.add(cr.value);
            }
            return new CoerceResult(true, out);
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map m) {
            MapKey keySchema = m.getMapKey();
            MapValue valSchema = m.getMapValue();
            Map<?,?> srcMap = parsed instanceof Map ? (Map<?,?>) parsed : null;
            if (srcMap == null) return new CoerceResult(false, null);
            Map<Object,Object> out = new LinkedHashMap<>();
            for (Map.Entry<?,?> en : srcMap.entrySet()) {
                Object rawK = en.getKey();
                Object rawV = en.getValue();
                CoerceResult ck = coerceAgainstSchema(keySchema == null ? null : (Schema) keySchema.getSchema(), rawK);
                if (!ck.ok) return new CoerceResult(false, null);
                CoerceResult cv = coerceAgainstSchema(valSchema == null ? null : (Schema) valSchema.getSchema(), rawV);
                if (!cv.ok) return new CoerceResult(false, null);
                out.put(ck.value, cv.value);
            }
            return new CoerceResult(true, out);
        }

        if (schema instanceof Enum es) {
            es.prints();
            List<EnumValue> values = es.getValues();
            if (values == null || values.isEmpty()) {
                return new CoerceResult(false, null);
            }

            for (EnumValue ev : values) {
                if (ev == null || ev.getValue() == null) continue;

                // get the declared numeric/string value for this enum entry
                Object enumRaw = (ev.getValue() == null) ? null : ev.getValue().raw();

                // Match either by the raw value (e.g. numeric 1) or by explicit name (e.g. "true")
                if (Objects.equals(enumRaw, parsed) || Objects.equals(ev.getName(), parsed)) {
                    if (enumRaw != null) {
                        return new CoerceResult(true, String.valueOf(enumRaw));
                    } else {
                        // fallback to DTDL name if no raw value present
                        return new CoerceResult(true, ev.getName());
                    }
                }
            }

            return new CoerceResult(false, null);
        }

        return new CoerceResult(true, parsed);
    }

    private static Object wrapIfNeeded(Object coercedValue, Schema schema, String telemetryNameForLookup, BindingRegistry.Binding binding) {
        String key = telemetryNameForLookup != null ? telemetryNameForLookup : (binding == null ? null : binding.telemetryName);

        if (key != null && schema != null && !(schema instanceof PrimitiveType)) {
            return Collections.singletonMap(key, coercedValue);
        }

        return coercedValue;
    }
}
