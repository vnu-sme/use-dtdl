package org.tzi.use.dtdl.DTDLModel.Schema.Enum;

public class EnumLiteral {
    private final Integer intValue;
    private final String stringValue;

    public EnumLiteral(int value) {
        this.intValue = value;
        this.stringValue = null;
    }

    public EnumLiteral(String value) {
        this.stringValue = value;
        this.intValue = null;
    }

    public boolean isInt() {
        return intValue != null;
    }

    public boolean isString() {
        return stringValue != null;
    }

    public Object raw() {
        return isInt() ? intValue : stringValue;
    }
}
