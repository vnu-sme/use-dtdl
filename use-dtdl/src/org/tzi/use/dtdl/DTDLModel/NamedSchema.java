package org.tzi.use.dtdl.DTDLModel;

import org.tzi.use.dtdl.DTDLModel.Schema.ComplexSchema;

public class NamedSchema extends ComplexSchema {
    protected String name;

    public void setName(String n) { this.name = n; }
    public String getName() { return name; }
}
