package org.tzi.use.dtdl.gui.instance;

import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.NamedSchema;
import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

import java.util.List;
import java.util.Map;

public final class SchemaInputFactory  {
    private SchemaInputFactory() {
    }

    public static Schema resolveNamedSchema(Schema s, Interface iface) {
        if (s == null) return null;

        if (s.getClass().getSimpleName().equals("NamedSchema")) {
            try {
                var ns = (NamedSchema) s;
                String nm = ns.getName();
                if (nm != null) {
                    var resolved = iface.getSchemas().get(nm);
                    if (resolved != null) return resolved;
                }
            } catch (ClassCastException ignored) {}
        }

        return s;
    }
}
