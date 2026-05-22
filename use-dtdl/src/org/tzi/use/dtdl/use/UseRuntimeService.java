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

    public Value buildUseValue(Type expectedType, Object raw) {
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
            Type elemType = seqType.elemType();

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

        if (expectedType instanceof CollectionType ct && raw instanceof Collection) {
            Type elemType = ct.elemType();

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

    private Value createDataTypeValue(MDataType dt, Map<String, Value> attributeValues) {

        System.err.println("========== [UseRuntimeService] createDataTypeValue ==========");
        System.err.println("Datatype: " + dt.name());
        System.err.println("Attributes incoming: " + attributeValues);

        Map<String, Value> varBindings = new HashMap<>(attributeValues);

        MInstance self = new MDataTypeValue(dt, dt.name(), varBindings);

        Value result = new DataTypeValueValue(dt, self, attributeValues);

        System.err.println("[UseRuntimeService] Wrapped into DataTypeValueValue = " + result);

        return result;
    }
}
