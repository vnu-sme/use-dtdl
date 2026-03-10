package org.tzi.use.dtdl.ast.schema;

public class ASTMapValue extends ASTSchema {
    public String name;
    public ASTSchema schema;

    @Override
    public void prints() {
        this.printsGeneralInfo();
        System.out.println("ASTMapValue.name: " + name);
        if (schema != null) {
            schema.prints();
        }
    }
}
