package org.tzi.use.dtdl.DTDLModel.Schema.Enum;

import org.tzi.use.dtdl.DTDLModel.NamedSchema;

public class EnumValue extends NamedSchema {
    private EnumLiteral value;

    public void setValue(EnumLiteral v) { this.value = v; }
    public EnumLiteral getValue() { return value; }

    @Override
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "EnumValue: " + safe(name));
        if (value != null) {
            System.out.println(ind + "  literal: " + String.valueOf(value.raw()));
        } else {
            System.out.println(ind + "  literal: (null)");
        }
    }
}
