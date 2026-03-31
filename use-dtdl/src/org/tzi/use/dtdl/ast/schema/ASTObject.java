package org.tzi.use.dtdl.ast.schema;

import java.util.List;

public class ASTObject extends ASTSchema {
    public List<ASTField> fields;

    @Override
    public void prints() {
        System.out.println("  Object (id=" + id + ", displayName=" + (displayName == null ? "<none>" : displayName) + ")");
        if (fields == null || fields.isEmpty()) {
            System.out.println("    fields: <none>");
        } else {
            System.out.println("    fields:");
            for (ASTField f : fields) {
                if (f != null) f.prints();
            }
        }
    }
}