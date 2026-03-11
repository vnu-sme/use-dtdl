package org.tzi.use.dtdl.ast.schema;

import org.tzi.use.dtdl.ast.ASTNode;

public class ASTField extends ASTNode {
    public String name;
    public ASTSchema schema;

    public void prints() {
        System.out.println("    Field: " + (name == null ? "<anon>" : name));
        this.printsGeneralInfo();
        if (schema != null) {
            System.out.println("      schema:");
            schema.prints();
        } else {
            System.out.println("      schema: <none>");
        }
    }
}