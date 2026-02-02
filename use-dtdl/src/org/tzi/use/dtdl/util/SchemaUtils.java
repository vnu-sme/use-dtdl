package org.tzi.use.dtdl.util;

import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.DTDLModel.Schema.Map.MapKey;
import org.tzi.use.dtdl.DTDLModel.Schema.Map.MapValue;
import org.tzi.use.dtdl.DTDLModel.Schema.Object.Field;
import org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchemaUtils {
    /**
     * Returns coerced object or null if value invalid for schema.
     */
    @SuppressWarnings("unchecked")
    public static Object coerceToSchemaRecursive(Schema schema, Object value) {
        if (schema == null) {
            return value;
        }
        if (value == null) return null;

        if (schema instanceof PrimitiveType pt) {
            String t = pt.getTypeName();
            if (t == null) t = "string";
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
            if (value instanceof String s) return s;
            return String.valueOf(value);
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum enm) {
            if (value instanceof String vs) {
                for (EnumValue ev : enm.getValues()) {
                    if (ev != null && ev.getName().equals(vs)) return vs;
                    if (ev != null && ev.getValue() != null) {
                        Object lit = ev.getValue().raw();
                        if (lit != null && lit.toString().equals(vs)) return vs;
                    }
                }
            }
            return null;
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object objSchema) {
            if (!(value instanceof Map<?, ?>)) {
                return null;
            }
            Map<String, Object> inMap = (Map<String,Object>) value;
            Map<String, Object> outMap = new LinkedHashMap<>();
            for (Field f : objSchema.getFields()) {
                String fname = f.getName();
                Schema fSchema = f.getSchema();
                Object rawField = inMap.get(fname);
                Object coercedField = coerceToSchemaRecursive(fSchema, rawField);
                if (rawField != null && coercedField == null) {
                    return null;
                }
                if (coercedField != null) outMap.put(fname, coercedField);
            }
            return outMap;
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map mapSchema) {
            if (!(value instanceof Map<?, ?>)) return null;
            Map<?,?> in = (Map<?,?>) value;
            Map<Object,Object> outMap = new LinkedHashMap<>();
            MapKey mk = mapSchema.getMapKey();
            MapValue mv = mapSchema.getMapValue();
            Schema keySchema = mk != null ? mk.getSchema() : null;
            Schema valueSchema = mv != null ? mv.getSchema() : null;
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

        if (schema instanceof Array arrSchema) {
            if (!(value instanceof List<?>)) return null;
            List<?> inList = (List<?>) value;
            List<Object> outList = new ArrayList<>();
            Schema elemSchema = arrSchema.getElementSchema();
            for (Object elem : inList) {
                Object coerced = coerceToSchemaRecursive(elemSchema, elem);
                if (elem != null && coerced == null) return null;
                outList.add(coerced != null ? coerced : elem);
            }
            return outList;
        }

        return value;
    }
}
