package org.tzi.use.dtdl.DTDLModel.Command;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

public class CommandPayload extends ContentElement {
    private boolean nullable;
    private Schema schema;

    public void setName(String name) { this.name = name; }

    public void setNullable(boolean n) { this.nullable = n; }

    public void setSchema(Schema s) { this.schema = s; }

    public boolean isNullable() { return nullable; }

    public Schema getSchema() { return schema; }

    @Override
    public void prints() { prints(0); }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "CommandPayload:");
        super.prints(indent + 2);
        System.out.println(ind + "  name: " + safe(name));
        System.out.println(ind + "  nullable: " + nullable);
        System.out.print(ind + "  schema: ");
        if (schema != null) {
            System.out.println();
            schema.prints(indent + 4);
        } else {
            System.out.println("(null)");
        }
    }
}

