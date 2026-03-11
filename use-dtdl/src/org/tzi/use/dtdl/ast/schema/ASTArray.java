package org.tzi.use.dtdl.ast.schema;

public class ASTArray extends ASTSchema {
    public ASTSchema elementSchema;

    @Override
    public void prints() {
        System.out.println("  Array (id=" + id + ")");
        if (elementSchema != null) {
            System.out.println("    elementSchema:");
            elementSchema.prints();
        } else {
            System.out.println("    elementSchema: <none>");
        }
    }
}