package org.tzi.use.dtdl.ast.schema;

public class ASTEnumValue extends ASTSchema {
    public String name;
    public ASTEnumLiteral value;

    @Override
    public void prints() {
        System.out.println("    - EnumValue: name=" + (name == null ? "<null>" : name));
        if (value != null) {
            System.out.print("      literal: ");
            value.prints();
        } else {
            System.out.println("      literal: <none>");
        }
    }
}
