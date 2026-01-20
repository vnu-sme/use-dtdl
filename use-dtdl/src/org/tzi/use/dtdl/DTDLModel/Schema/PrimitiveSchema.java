package org.tzi.use.dtdl.DTDLModel.Schema;

// DEPRECATED
public class PrimitiveSchema {
    // Could be enum, string type, etc.
    private final Object value;

    public PrimitiveSchema(String v)  { value = v; }
    public PrimitiveSchema(boolean v)  { value = v; }
    public PrimitiveSchema(byte v)     { value = v; }
    public PrimitiveSchema(short v)    { value = v; }
    public PrimitiveSchema(int v)      { value = v; }
    public PrimitiveSchema(long v)     { value = v; }
    public PrimitiveSchema(float v)    { value = v; }
    public PrimitiveSchema(double v)   { value = v; }
    public PrimitiveSchema(char v)     { value = v; }

    public Object value() {
        return value;
    }
}
