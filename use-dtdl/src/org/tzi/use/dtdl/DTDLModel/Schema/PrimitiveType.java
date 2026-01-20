package org.tzi.use.dtdl.DTDLModel.Schema;

public class PrimitiveType extends Schema {
    private final String typeName;
    public PrimitiveType(String typeName) { this.typeName = typeName; }
    public String getTypeName() { return typeName; }

    @Override
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "PrimitiveType: " + safe(typeName));
    }

}
