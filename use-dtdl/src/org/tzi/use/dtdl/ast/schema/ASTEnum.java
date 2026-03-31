package org.tzi.use.dtdl.ast.schema;

import java.util.List;

public class ASTEnum extends ASTSchema {
    public ASTPrimitiveSchema valueSchema;
    public List<ASTEnumValue> enumValues;

    @Override
    public void prints() {
        System.out.println("  Enum (id=" + id + ", displayName=" + (displayName == null ? "<none>" : displayName) + ")");
        if (valueSchema != null) {
            System.out.print("    valueSchema: ");
            valueSchema.prints();
        } else {
            System.out.println("    valueSchema: <none>");
        }
        if (enumValues == null || enumValues.isEmpty()) {
            System.out.println("    enumValues: <none>");
        } else {
            System.out.println("    enumValues:");
            for (ASTEnumValue v : enumValues) {
                if (v != null) v.prints();
            }
        }
    }
}
