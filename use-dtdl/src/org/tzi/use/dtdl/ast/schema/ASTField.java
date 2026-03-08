package org.tzi.use.dtdl.ast.schema;

import org.tzi.use.dtdl.ast.ASTNode;

public class ASTField extends ASTNode {
    public String name;
    public ASTSchema schema;

    public void prints() {
        this.printsGeneralInfo();
        System.out.println("ASTField.name: " + name);

        if (schema != null) {
            schema.prints();
        } else {
            System.out.println("ASTField.schema: null");
        }
    }
}