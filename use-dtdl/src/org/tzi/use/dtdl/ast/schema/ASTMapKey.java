package org.tzi.use.dtdl.ast.schema;

public class ASTMapKey extends ASTSchema {
    public String name;
    public ASTSchema schema;

    @Override
    public void prints() {
        System.out.println("      MapKey: name=" + (name == null ? "<null>" : name));
        if (schema != null) {
            System.out.println("        schema:");
            schema.prints();
        } else {
            System.out.println("        schema: <none>");
        }
    }
}
