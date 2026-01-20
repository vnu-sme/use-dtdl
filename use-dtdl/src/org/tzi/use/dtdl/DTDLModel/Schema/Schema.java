package org.tzi.use.dtdl.DTDLModel.Schema;

import org.tzi.use.dtdl.DTDLModel.Element;

public abstract class Schema extends Element {
    // intentionally empty (root type)

    // in Schema base
    public void prints() { prints(0); }
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Schema: (unknown subtype: " + getClass().getSimpleName() + ")");
    }
}

