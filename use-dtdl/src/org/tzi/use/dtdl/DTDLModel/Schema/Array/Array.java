package org.tzi.use.dtdl.DTDLModel.Schema.Array;

import org.tzi.use.dtdl.DTDLModel.Schema.ComplexSchema;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

public class Array extends ComplexSchema {
    private Schema elementSchema;

    public void setElementSchema(Schema s) { this.elementSchema = s; }
    public Schema getElementSchema() { return this.elementSchema; }

    @Override
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Array:");
        if (getElementSchema() == null) {
            System.out.println(ind + "  elementSchema: (null)");
        } else {
            System.out.println(ind + "  elementSchema:");
            getElementSchema().prints(indent + 4);
        }
    }
}
