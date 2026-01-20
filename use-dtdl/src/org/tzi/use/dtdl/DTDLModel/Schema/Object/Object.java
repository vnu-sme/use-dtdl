package org.tzi.use.dtdl.DTDLModel.Schema.Object;

import org.tzi.use.dtdl.DTDLModel.Schema.ComplexSchema;

import java.util.ArrayList;
import java.util.List;

public class Object extends ComplexSchema {
    private final List<Field> fields = new ArrayList<>();

    public void addField(Field f) { fields.add(f); }

    public List<Field> getFields() { return fields; }

    // Object
    @Override
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Object:");
        if (fields.isEmpty()) {
            System.out.println(ind + "  fields: (none)");
        } else {
            System.out.println(ind + "  fields:");
            for (Field f : fields) {
                f.prints(indent + 4);
            }
        }
    }
}
