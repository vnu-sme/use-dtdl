package org.tzi.use.dtdl.DTDLModel.Schema.Map;

import org.tzi.use.dtdl.DTDLModel.NamedSchema;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

public class MapKey extends NamedSchema {
    private Schema schema; // may be primitive or complex

    public MapKey(String name, Schema schema) { this.name = name; this.schema = schema; }

    public String getName() { return name; }

    public Schema getSchema() { return schema; }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + this.getClass().getSimpleName() + ": " + safe(name));
        {
            Schema s = (this).getSchema();
            if (s != null) {
                s.prints(indent + 2);
            }
        }
    }
}

