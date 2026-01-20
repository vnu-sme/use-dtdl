package org.tzi.use.dtdl.DTDLModel.Schema.Object;

import org.tzi.use.dtdl.DTDLModel.NamedSchema;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

public class Field extends NamedSchema {
    private Schema schema;

    public Field(String name, Schema schema) { this.name = name; this.schema = schema; }

    public String getName() { return name; }

    public Schema getSchema() { return schema; }

    // Field
    @Override
    public void prints() { prints(0); }
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Field: " + safe(name));
        if (schema == null) {
            System.out.println(ind + "  schema: (null)");
        } else {
            System.out.println(ind + "  schema:");
            schema.prints(indent + 4);
        }
    }

}
