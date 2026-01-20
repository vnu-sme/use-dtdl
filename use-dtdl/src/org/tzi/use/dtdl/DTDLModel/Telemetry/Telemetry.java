package org.tzi.use.dtdl.DTDLModel.Telemetry;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

public class Telemetry extends ContentElement {
    private Schema schema;

    public Telemetry(String id) { this.id = id; this.type = "Telemetry"; }

    public void setName(String name) { this.name = name; }
    public void setSchema(Schema s) { this.schema = s; }
    public Schema getSchema() { return schema; }

    @Override
    public void prints() { prints(0); }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Telemetry:");
        super.prints(indent + 2);
        System.out.println(ind + "  name: " + safe(name));
        System.out.print(ind + "  schema: ");
        if (schema != null) {
            System.out.println();
            schema.prints(indent + 4);
        } else {
            System.out.println("(null)");
        }
    }
}