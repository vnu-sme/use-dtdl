package org.tzi.use.dtdl.ast.schema;

public class ASTMapKey extends ASTSchema {
    public String name;
    public ASTSchema schema;

    @Override
    public void prints() {
        this.printsGeneralInfo();
        System.out.println("ASTMapKey.name: " + name);
        System.out.println("ASTMapKey.schema: " + schema);
        if (schema != null) {
            schema.prints();
        }
    }
}
