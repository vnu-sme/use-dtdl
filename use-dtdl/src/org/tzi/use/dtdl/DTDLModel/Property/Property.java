package org.tzi.use.dtdl.DTDLModel.Property;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

public class Property extends ContentElement {
    private Schema schema;
    private boolean writable;

    public Property(String id) {
        this.id = id;
        this.type = "Property";
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSchema(Schema s) {
        this.schema = s;
    }

    public Schema getSchema() {
        return schema;
    }

    public void setWritable(boolean w) {
        this.writable = w;
    }

    public boolean isWritable() {
        return writable;
    }

    @Override
    public void prints() { prints(0); }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Property:");
        super.prints(indent + 2);
        System.out.println(ind + "  name: " + safe(name));
        System.out.println(ind + "  writable: " + writable);
        System.out.print(ind + "  schema: ");
        if (schema != null) {
            System.out.println();
            schema.prints(indent + 4);
        } else {
            System.out.println("(null)");
        }
    }
}
