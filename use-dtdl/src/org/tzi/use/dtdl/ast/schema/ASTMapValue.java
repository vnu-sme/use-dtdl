package org.tzi.use.dtdl.ast.schema;

public class ASTMapValue extends ASTSchema {
    public String name;
    public ASTSchema schema;

    @Override
    public void prints() {
        System.out.println("      MapValue: name=" + (name == null ? "<null>" : name));
        if (schema != null) {
            System.out.println("        schema:");
            schema.prints();
        } else {
            System.out.println("        schema: <none>");
        }
    }
}
