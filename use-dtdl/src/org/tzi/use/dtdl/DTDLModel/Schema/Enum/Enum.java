package org.tzi.use.dtdl.DTDLModel.Schema.Enum;

import org.tzi.use.dtdl.DTDLModel.Schema.ComplexSchema;
import org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType;

import java.util.ArrayList;
import java.util.List;

public class Enum extends ComplexSchema {
    private PrimitiveType valueSchema;
    private final List<EnumValue> values = new ArrayList<>();

    public void setValueSchema(PrimitiveType s) { this.valueSchema = s; }

    public PrimitiveType getValueSchema() { return valueSchema; }

    public void addEnumValue(EnumValue v) { values.add(v); }

    public List<EnumValue> getValues() { return values; }

    @Override
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Enum:");
        if (getValueSchema() != null) {
            System.out.println(ind + "  valueSchema: " + valueSchema.getTypeName());
        } else {
            System.out.println(ind + "  valueSchema: (unset)");
        }
        if (getValues().isEmpty()) {
            System.out.println(ind + "  values: (none)");
        } else {
            System.out.println(ind + "  values:");
            for (EnumValue ev : getValues()) ev.prints(indent + 4);
        }
    }
}


