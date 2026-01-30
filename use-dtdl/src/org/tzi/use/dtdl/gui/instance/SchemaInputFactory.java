package org.tzi.use.dtdl.gui.instance;

import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.NamedSchema;
import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

import java.util.List;
import java.util.Map;

public final class SchemaInputFactory  {
    private SchemaInputFactory() {
    }

    public static Schema resolveNamedSchema(Schema s, Interface iface) {
        if (s == null) return null;

        if (s.getClass().getSimpleName().equals("NamedSchema")) {
            try {
                var ns = (NamedSchema) s;
                String nm = ns.getName();
                if (nm != null) {
                    var resolved = iface.getSchemas().get(nm);
                    if (resolved != null) return resolved;
                }
            } catch (ClassCastException ignored) {}
        }

        return s;
    }

    public static Object tryCoerceToSchema(Object v, Schema schema) {
        if (schema == null) return v;
        if (v == null) return null;

        if (schema instanceof PrimitiveType pt) {
            String t = pt.getTypeName();
            if (v instanceof String vs) {
                String s = vs.trim();
                switch (t) {
                    case "boolean": if ("true".equalsIgnoreCase(s)) return Boolean.TRUE; if ("false".equalsIgnoreCase(s)) return Boolean.FALSE; return null;
                    case "integer": try { return Integer.parseInt(s); } catch(Exception ex) { return null; }
                    case "long": try { return Long.parseLong(s); } catch(Exception ex) { return null; }
                    case "double": case "float": try { return Double.parseDouble(s); } catch(Exception ex) { return null; }
                    default: return s;
                }
            } else if (v instanceof Number n) {
                return switch (t) {
                    case "integer" -> n.intValue();
                    case "long" -> n.longValue();
                    case "double", "float" -> n.doubleValue();
                    default -> v;
                };
            } else if (v instanceof Boolean && "boolean".equals(t)) return v;
            else return null;
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum e) {
            if (v instanceof String vs) {
                for (EnumValue ev : e.getValues()) if (ev != null && ev.getName().equals(vs)) return vs;
            }
            return null;
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object) {
            if (v instanceof Map) return v;
            return null;
        }

        if (schema instanceof Array) {
            if (v instanceof List) return v;
            return null;
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map) {
            if (v instanceof Map) return v;
            return null;
        }

        return v;
    }
}
