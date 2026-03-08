package org.tzi.use.dtdl.use;

import org.tzi.use.api.UseModelApi;
import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.util.Utils;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MDataTypeValue;
import org.tzi.use.uml.sys.MInstance;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

import java.util.*;

public final class UseRuntimeService {

    private final Session session;

    public UseRuntimeService(Session session) {
        this.session = session;
    }

    public List<String> listUseObjectsForDtInterface(String targetIfaceId) {
        if (targetIfaceId == null) return Collections.emptyList();
        DTDLModelRegistry registry = DTDLPluginState.registry();

        Optional<String> mapped = registry != null ? registry.getClassNameForDtmi(targetIfaceId) : Optional.empty();
        if (mapped.isEmpty()) {
            throw new IllegalStateException("No USE class mapping for DTMI: " + targetIfaceId);
        }
        String targetClass = mapped.get();

        if (session == null || session.system() == null) return Collections.emptyList();
        Set<MObject> all = session.system().state().allObjects();
        List<String> names = new ArrayList<>();
        for (MObject o : all) {
            MClass cls = o.cls();
            if (cls != null && Objects.equals(cls.name(), targetClass)) {
                names.add(o.name());
            }
        }
        return names;
    }


    /* =========================
     * System bootstrap
     * ========================= */

    public void ensureSystemExists() {
        boolean needCreate = false;
        try {
            if (session.system() == null) needCreate = true;
            else if (session.system().model() == null) needCreate = true;
        } catch (Throwable t) {
            needCreate = true;
        }

        if (needCreate) {
            String modelName = "unnamed";
            UseModelApi tmp = new UseModelApi(modelName);
            MModel m = tmp.getModel();
            MSystem s = new MSystem(m);
            session.setSystem(s);

            try {
                session.system().ensureStateLinkSetsForModel();
                session.system().state().updateDerivedValues(true);
            } catch (Throwable t) {
                /* ignore */
            }
        }
    }

    /* =========================
     * Value conversion
     * ========================= */

    public Value buildUseValue(org.tzi.use.uml.ocl.type.Type expectedType, Object raw) {
        if (raw == null) return UndefinedValue.instance;

        if (raw instanceof Boolean) {
            return BooleanValue.get((Boolean) raw);
        }

        if (raw instanceof Integer) {
            return new IntegerValue((Integer) raw);
        }

        if (raw instanceof Long) {
            return new IntegerValue(((Long) raw).intValue());
        }

        if (raw instanceof Double || raw instanceof Float) {
            return new RealValue(((Number) raw).doubleValue());
        }

        if (expectedType instanceof StringType && raw instanceof String) {
            return new StringValue((String) raw);
        }

        if (expectedType instanceof EnumType && raw instanceof String) {
            return new org.tzi.use.uml.ocl.value.EnumValue((EnumType) expectedType, (String) raw);
        }

        if (expectedType instanceof SequenceType seqType && raw instanceof Map<?, ?> map) {
            org.tzi.use.uml.ocl.type.Type elemType = seqType.elemType();

            // collect entry values into a list (cannot call SequenceValue.add because it's not public)
            List<Value> elements = new ArrayList<>();

            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(elemType instanceof MDataType entryType)) {
                    throw new IllegalArgumentException("Map entry element is not a DataType: " + elemType);
                }

                Map<String, Value> entryValues = new LinkedHashMap<>();

                for (MAttribute a : entryType.attributes()) {
                    if ("key".equals(a.name())) {
                        entryValues.put("key", buildUseValue(a.type(), e.getKey()));
                    } else if ("value".equals(a.name())) {
                        entryValues.put("value", buildUseValue(a.type(), e.getValue()));
                    } else {
                        // if entryType has other attributes, try to map by name (optional)
                        Object rawVal = null;
                        // no raw source for other attributes -> leave undefined
                        entryValues.put(a.name(), UndefinedValue.instance);
                    }
                }

                elements.add(createDataTypeValue(entryType, entryValues));
            }

            return new SequenceValue(elemType, elements);
        }

        // --- handle arrays of datatypes: Sequence(MyObject) with raw being a Collection of Map -->
        if (expectedType instanceof SequenceType seqType2 && raw instanceof Collection<?> rawCol) {
            org.tzi.use.uml.ocl.type.Type elemType = seqType2.elemType();
            if (elemType instanceof MDataType elementDt) {
                List<Value> elements = new ArrayList<>();
                for (Object item : rawCol) {
                    if (item instanceof Map<?, ?> itemMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rawValues = (Map<String, Object>) itemMap;
                        Map<String, Value> values = new LinkedHashMap<>();
                        for (MAttribute a : elementDt.attributes()) {
                            Object attrRaw = rawValues.get(a.name());
                            values.put(a.name(), buildUseValue(a.type(), attrRaw));
                        }
                        elements.add(createDataTypeValue(elementDt, values));
                    } else {
                        // fallback: try to build element value normally
                        elements.add(buildUseValue(elemType, item));
                    }
                }
                return new SequenceValue(elemType, elements);
            }
        }

        if (expectedType instanceof MDataType && raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawValues = (Map<String, Object>) raw;

            Map<String, Value> values = new LinkedHashMap<>();
            MDataType dt = (MDataType) expectedType;

            for (MAttribute attr : dt.attributes()) {
                Object attrRaw = rawValues.get(attr.name());
                Value attrValue = buildUseValue(attr.type(), attrRaw);
                values.put(attr.name(), attrValue);
            }

            return createDataTypeValue(dt, values);
        }

        if (expectedType instanceof CollectionType && raw instanceof Collection) {
            CollectionType ct = (CollectionType) expectedType;
            org.tzi.use.uml.ocl.type.Type elemType = ct.elemType();

            Collection<Value> elements = new ArrayList<>();
            for (Object o : (Collection<?>) raw) {
                elements.add(buildUseValue(elemType, o));
            }

            if (ct instanceof SetType) return new SetValue(elemType, elements);
            if (ct instanceof SequenceType) return new SequenceValue(elemType, elements);
            if (ct instanceof BagType) return new BagValue(elemType, elements);
            if (ct instanceof OrderedSetType) return new OrderedSetValue(elemType, elements);
        }

        if (expectedType instanceof MClassifier && raw instanceof MObject) {
            return new ObjectValue((MClassifier) expectedType, (MObject) raw);
        }

        throw new IllegalArgumentException(
                "Cannot build USE value for type=" + expectedType + ", raw=" + raw
        );
    }

    public String toOclLiteral(Object v, org.tzi.use.uml.ocl.type.Type expectedType) {
        if (v == null) return "null";

        try {
            if (expectedType instanceof EnumType et && v instanceof String) {
                String lit = ((String) v).trim();
                String enumTypeName;
                try {
                    enumTypeName = et.name();
                } catch (Throwable t) {
                    enumTypeName = et.toString();
                }
                return enumTypeName + "::" + lit;
            }
        } catch (Throwable ignored) {
        }

        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof Number n) return n.toString();

        if (v instanceof String s) {
            String esc = s.replace("\\", "\\\\").replace("'", "\\'");
            return "'" + esc + "'";
        }

        String esc = String.valueOf(v).replace("\\", "\\\\").replace("'", "\\'");
        return "'" + esc + "'";
    }

    /* =========================
     * Association lookup
     * ========================= */

    public String findAssociationBetweenClassesForRole(
            String srcObjectName,
            String tgtObjectName,
            String roleName
    ) {
        if (session == null || session.system() == null) return null;

        MModel mm = session.system().model();
        if (mm == null) return null;

        MObject src = session.system().state().objectByName(srcObjectName);
        MObject tgt = session.system().state().objectByName(tgtObjectName);
        if (src == null || tgt == null) return null;

        String clsA = src.cls() != null ? src.cls().name() : null;
        String clsB = tgt.cls() != null ? tgt.cls().name() : null;
        if (clsA == null || clsB == null) return null;

        for (MAssociation a : mm.associations()) {
            List<MAssociationEnd> ends = a.associationEnds();
            if (ends.size() != 2) continue;

            MAssociationEnd e1 = ends.get(0);
            MAssociationEnd e2 = ends.get(1);

            boolean match =
                    Objects.equals(e1.cls().name(), clsA) &&
                            Objects.equals(e2.cls().name(), clsB) &&
                            (roleName == null ||
                                    Objects.equals(e1.name(), roleName) ||
                                    Objects.equals(e2.name(), roleName));

            boolean reverse =
                    Objects.equals(e1.cls().name(), clsB) &&
                            Objects.equals(e2.cls().name(), clsA) &&
                            (roleName == null ||
                                    Objects.equals(e1.name(), roleName) ||
                                    Objects.equals(e2.name(), roleName));

            if (match || reverse) return a.name();
        }

        return null;
    }

    private Value createDataTypeValue(MDataType dt, Map<String, Value> attributeValues) {

        System.err.println("========== [UseRuntimeService] createDataTypeValue ==========");
        System.err.println("Datatype: " + dt.name());
        System.err.println("Attributes incoming: " + attributeValues);

        Map<String, Value> varBindings = new HashMap<>(attributeValues);

        MInstance self = new MDataTypeValue(
                dt,
                dt.name(),
                varBindings
        );

        System.err.println("[UseRuntimeService] Created MDataTypeValue self = " + self);
        System.err.println("[UseRuntimeService] self.cls() = " + (self != null ? self.cls() : "<null>"));

        Value result = new DataTypeValueValue(dt, self, attributeValues);

        System.err.println("[UseRuntimeService] Wrapped into DataTypeValueValue = " + result);

        return result;
    }
}
